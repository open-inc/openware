package de.openinc.api;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.DateTool;
import org.json.JSONArray;
import org.json.JSONObject;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;

/**
 * interface to implement {@link ActuatorAdapter}s that can be used to perform
 * actions in open.WARE
 * 
 * @author MartinStein
 * 
 */
public abstract class ActuatorAdapter {
	private ExecutorService executor;

	/**
	 * Sends the provided {@code payload} via an ActuatorAdapter. Depending on the
	 * Adapter the parameters have different meanings. The typical idea behind the
	 * parameters is described below.
	 * 
	 * @param target       The target of the action, e.g. an mail adress or an
	 *                     URL-Endpoint
	 * @param topic        The topic / subject / title of the action. E.g. the
	 *                     subject of an mail or Channel of a Push-Notfication
	 * @param payload      The payload which will be transfered. E.g. the mail body
	 *                     or the body of an POST-Request
	 * @param options      {@link JSONObject} containing information relevant to the
	 *                     ActuatorAdapter
	 * 
	 * @param optionalData List of {@link OpenWareDataItem} containing optional data
	 *                     to be processed within the ActuatorAdapter
	 */
	final public Future<Object> send(String target, String topic, String payload, User user, JSONObject options,
			List<OpenWareDataItem> optionalData) throws Exception {
		JSONObject params = new JSONObject();
		JSONArray data = new JSONArray();
		for (OpenWareDataItem item : optionalData) {
			data.put(item.toJSON());
		}
		params.put("target", target);
		params.put("topic", topic);
		params.put("payload", payload);
		params.put("options", options);
		params.put("data", data.get(0));
		params.put("datasets", data);
		params.put("user", user == null ? new JSONObject() : user.toJSON());
		if (options.has("templateType") && options.optString("templateType").toLowerCase().equals("vtl")) {
			VelocityContext c = new VelocityContext();
			c.put("target", params.get("target"));
			c.put("topic", params.get("topic"));
			c.put("payload", params.get("payload"));
			c.put("options", params.get("options"));
			c.put("data", params.get("data"));
			c.put("datasets", params.get("datasets"));
			c.put("user", params.get("user"));
			c.put("datetool", new DateTool());
			final String templatedTarget = applyVelocityTemplate(target, c);
			final String templatedTopic = applyVelocityTemplate(topic, c);
			final String templatedPayload = applyVelocityTemplate(payload, c);
			final JSONObject templatedOptions = new JSONObject(applyVelocityTemplate(options.toString(), c));

			return executor.submit(new Callable<Object>() {

				@Override
				public Object call() throws Exception {
					try {
						return processAction(templatedTarget, templatedTopic, templatedPayload, user, templatedOptions,
								optionalData, c);
					} catch (Exception e) {
						OpenWareInstance.getInstance().logError("Could not process Action\n" + target + "\n" + topic
								+ "\n" + payload + "\n" + options.toString(2) + "\n", e);
						return null;
					}
				}
			});
		} else {
			Object jsonDOC = Configuration.defaultConfiguration().jsonProvider().parse(params.toString());
			final String templatedTarget = applyJPTemplate(target, jsonDOC);
			final String templatedTopic = applyJPTemplate(topic, jsonDOC);
			final String templatedPayload = applyJPTemplate(payload, jsonDOC);
			final JSONObject templatedOptions = new JSONObject(applyJPTemplate(options.toString(), jsonDOC));

			return executor.submit(new Callable<Object>() {

				@Override
				public Object call() throws Exception {
					try {
						return processAction(templatedTarget, templatedTopic, templatedPayload, user, templatedOptions,
								optionalData, jsonDOC);
					} catch (Exception e) {
						OpenWareInstance.getInstance().logError("Could not process Action\n" + target + "\n" + topic
								+ "\n" + payload + "\n" + options.toString(2) + "\n", e);
						return null;
					}
				}
			});
		}

	}

	public String applyTemplate(String s, Object context) {
		if (context instanceof VelocityContext) {
			return applyVelocityTemplate(s, (VelocityContext) context);
		}
		return applyJPTemplate(s, context);
	}

	private String applyJPTemplate(String s, Object jsonDOC) {
		String result = s;
		String pre = "{{#";
		String suf = "#}}";
		String inner = null;
		int closingIndex = result.indexOf(suf);
		int openingIndex = result.lastIndexOf(pre, closingIndex);
		while (closingIndex != -1 && openingIndex != -1) {
			inner = result.substring(openingIndex + pre.length(), closingIndex);
			String toReplace = pre + inner + suf;
			String res = "" + JsonPath.read(jsonDOC, inner);
			result = result.replace(toReplace, res);
			closingIndex = result.indexOf(suf);
			openingIndex = result.lastIndexOf(pre, closingIndex);
		}
		OpenWareInstance.getInstance()
				.logTrace("[TEMPLATING]Replaced before processing action:\nOld: " + s + "\nNew: " + result);
		return result.replace("\\}", "}").replace("\\{", "{");
	}

	private String applyVelocityTemplate(String s, VelocityContext contextDoc) {
		Velocity.init();
		Reader r = new StringReader(s);
		Writer w = new StringWriter();
		Velocity.evaluate(contextDoc, w, s, r);
		return w.toString();
	}

	protected abstract Object processAction(String target, String topic, String payload, User user, JSONObject options,
			List<OpenWareDataItem> optionalData, Object optionalTemplateOptions) throws Exception;

	public abstract String getType();

	final public void init(JSONObject options, boolean really) throws Exception {
		executor = Executors.newSingleThreadExecutor();
		if (really)
			init(options);
	}

	public abstract void init(JSONObject options) throws Exception;

}

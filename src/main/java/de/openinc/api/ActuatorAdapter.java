package de.openinc.api;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.json.JSONArray;
import org.json.JSONObject;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.TransformationService;

/**
 * interface to implement {@link ActuatorAdapter}s that can be used to perform actions in open.WARE
 * 
 * @author MartinStein
 * 
 */
public abstract class ActuatorAdapter {
	private ExecutorService executor;

	/**
	 * Sends the provided {@code payload} via an ActuatorAdapter. Depending on the Adapter the
	 * parameters have different meanings. The typical idea behind the parameters is described
	 * below.
	 * 
	 * @param target The target of the action, e.g., an email address or a URL endpoint.
	 * @param topic The topic/subject/title of the action, e.g., the subject of an email or the
	 *        channel of a push notification.
	 * @param payload The payload to be transferred, e.g., the email body or the body of a POST
	 *        request.
	 * @param user The user initiating the action.
	 * @param options {@link JSONObject} containing information relevant to the ActuatorAdapter.
	 * @param optionalData List of {@link OpenWareDataItem} containing optional data to be processed
	 *        within the ActuatorAdapter.
	 * @return A {@link Future} representing the result of the asynchronous operation.
	 * @throws Exception If an error occurs during the action processing.
	 */
	final public Future<Object> send(String target, String topic, String payload, User user,
			JSONObject options, List<OpenWareDataItem> optionalData) throws Exception {
		JSONObject params = new JSONObject();
		JSONArray data = options.optJSONArray("data") == null ? new JSONArray()
				: options.getJSONArray("data");
		if (optionalData != null) {
			for (OpenWareDataItem item : optionalData) {
				data.put(item.toJSON());
			}
		}
		if (options.has("fetchData")) {
			JSONArray fetchData = options.getJSONArray("fetchData");
			for (int i = 0; i < fetchData.length(); i++) {
				try {
					OpenWareDataItem item = TransformationService.getInstance().pipeOperations(user,
							null, fetchData.getJSONObject(i));
					data.put(item.toJSON());
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError(
							"Could not fetch data for action:\n" + fetchData.optJSONObject(i), e);
					continue;
				}

			}
		}
		params.put("target", target);
		params.put("topic", topic);
		params.put("payload", payload);
		params.put("options", options);
		if (data.length() > 0) {
			params.put("data", data.get(0));
		}
		params.put("datasets", data);
		params.put("user", user == null ? new JSONObject() : user.toJSON());
		if (options != null && options.has("templateType")
				&& options.optString("templateType").toLowerCase().equals("vtl")) {
			VelocityContext c = new VelocityContext();
			c.put("target", params.get("target"));
			c.put("topic", params.get("topic"));
			c.put("payload", params.get("payload"));
			c.put("options", params.get("options"));
			c.put("data", params.opt("data"));
			c.put("datasets", params.get("datasets"));
			c.put("user", params.get("user"));
			c.put("datetool", new DateTool());
			c.put("esc", new EscapeTool());
			final String templatedTarget = applyVelocityTemplate(target, c);
			final String templatedTopic = applyVelocityTemplate(topic, c);
			final String templatedPayload = applyVelocityTemplate(payload, c);
			String json = options.toString();
			String processed = applyVelocityTemplate(json, c);

			final JSONObject templatedOptions = new JSONObject(processed);

			return CompletableFuture.supplyAsync(() -> {

				try {
					return processAction(templatedTarget, templatedTopic, templatedPayload, user,
							templatedOptions, optionalData, c);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

			}, executor);

		} else {
			Object jsonDOC =
					Configuration.defaultConfiguration().jsonProvider().parse(params.toString());
			final String templatedTarget = applyJPTemplate(target, jsonDOC);
			final String templatedTopic = applyJPTemplate(topic, jsonDOC);
			final String templatedPayload = applyJPTemplate(payload, jsonDOC);
			JSONObject templatedOptions = new JSONObject();
			if (options != null) {
				String processed = applyJPTemplate(options.toString(), jsonDOC);
				processed = removeQuotes(processed);
				templatedOptions = new JSONObject();
			}
			final JSONObject templateOptionsFinal = templatedOptions;

			return CompletableFuture.supplyAsync(() -> {
				try {
					return processAction(templatedTarget, templatedTopic, templatedPayload, user,
							templateOptionsFinal, optionalData, jsonDOC);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

			}, executor);


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
		OpenWareInstance.getInstance().logTrace(
				"[TEMPLATING]Replaced before processing action:\nOld: " + s + "\nNew: " + result);
		return result.replace("\\}", "}").replace("\\{", "{");
	}

	private String removeQuotes(String s) {
		String pre = "<|";
		String suf = "|>";
		StringBuilder sb = new StringBuilder(s);
		int closingIndex = sb.indexOf(suf);
		int openingIndex = sb.lastIndexOf(pre, closingIndex);

		while (closingIndex != -1 && openingIndex != -1) {
			if (closingIndex + suf.length() == sb.length()) {
				sb.delete(closingIndex, closingIndex + (suf.length()));
			} else {
				sb.delete(closingIndex, closingIndex + (suf.length() + 1));
			}

			if (openingIndex == 0) {
				sb.delete(openingIndex, openingIndex + (pre.length()));
			} else {
				sb.delete(openingIndex - 1, openingIndex + (pre.length()));
			}

			closingIndex = sb.indexOf(suf);
			openingIndex = sb.lastIndexOf(pre, closingIndex);
		}
		return sb.toString();
	}

	private String applyVelocityTemplate(String s, VelocityContext contextDoc) {
		Velocity.init();
		Reader r = new StringReader(s);
		Writer w = new StringWriter();
		Velocity.evaluate(contextDoc, w, s, r);

		return removeQuotes(w.toString());
	}

	protected abstract Object processAction(String target, String topic, String payload, User user,
			JSONObject options, List<OpenWareDataItem> optionalData, Object optionalTemplateOptions)
			throws Exception;

	public abstract String getType();

	final public void init(JSONObject options, boolean really) throws Exception {
		executor = OpenWareInstance.getInstance().getCommonExecuteService();
		if (really)
			init(options);
	}


	public abstract void init(JSONObject options) throws Exception;

}

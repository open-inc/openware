package de.openinc.api;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;

public abstract class ActuatorAdapter {
	private Pattern pattern = Pattern.compile(Config.templatingRegexSelector);
	private Pattern section = Pattern.compile(Config.templatingSectionRegexSelector);
	private ExecutorService executor;

	/**
	 * Sends the provided {@code payload} via an ActuatorAdapter. Depending on the
	 * Adapter the parameters have different meanings. The typical idea behind the
	 * parameters is described below.
	 * 
	 * @param target
	 *            The target of the action, e.g. an mail adress or an URL-Endpoint
	 * @param topic
	 *            The topic / subject / title of the action. E.g. the subject of an
	 *            mail or Channel of a Push-Notfication
	 * @param payload
	 *            The payload which will be transfered. E.g. the mail body or the
	 *            body of an POST-Request
	 * @param options
	 *            {@link JSONObject} containing information relevant to the
	 *            ActuatorAdapter
	 * 
	 * @param optionalData
	 *            {@link OpenWareDataItem} containing optional data to be processed
	 *            within the ActuatorAdapter
	 */
	final public Future<Object> send(String target, String topic, String payload, User user, JSONObject options,
			OpenWareDataItem optionalData)
			throws Exception {
		JSONObject params = new JSONObject();
		params.put("target", target);
		params.put("topic", topic);
		params.put("payload", payload);
		params.put("options", options);
		params.put("data", optionalData.toJSON());
		params.put("user", user.toJSON());
		Object jsonDOC = Configuration.defaultConfiguration().jsonProvider().parse(params.toString());
		final String templatedTarget = applyTemplate(target, jsonDOC);
		final String templatedTopic = applyTemplate(topic, jsonDOC);
		final String templatedPayload = applyTemplate(payload, jsonDOC);
		final JSONObject templatedOptions = new JSONObject(applyTemplate(options.toString(), jsonDOC));
		return executor.submit(new Callable<Object>() {

			@Override
			public Object call() throws Exception {
				try {
					return processAction(templatedTarget, templatedTopic, templatedPayload, user, templatedOptions,
							optionalData, jsonDOC);
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("Could not process Action\n" +	target +
															"\n" +
															topic +
															"\n" +
															payload +
															"\n" +
															options.toString(2) +
															"\n",
							e);
					return null;
				}
			}
		});
	}

	public String applyTemplate(String s, Object jsonDOC) {
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
		OpenWareInstance.getInstance().logTrace("[TEMPLATING]Replaced before processing action:\nOld: " +	s +
												"\nNew: " +
												result);
		return result.replace("\\}", "}").replace("\\{", "{");
	}

	protected abstract Object processAction(String target, String topic, String payload, User user,
			JSONObject options,
			OpenWareDataItem optionalData, Object optionalTemplateOptions) throws Exception;

	public abstract String getType();

	final public void init(JSONObject options, boolean really) throws Exception {
		executor = Executors.newSingleThreadExecutor();
		if (really)
			init(options);
	}

	public abstract void init(JSONObject options) throws Exception;

}

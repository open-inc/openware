package de.openinc.api;

import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;

public abstract class ActuatorAdapter {
	private Pattern pattern = Pattern.compile(Config.templatingRegexSelector);
	private Pattern section = Pattern.compile(Config.templatingSectionRegexSelector);

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
	final public Future<String> send(String target, String topic, String payload, User user, JSONObject options,
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

		target = applyTemplate(target, jsonDOC);
		topic = applyTemplate(topic, jsonDOC);
		payload = applyTemplate(payload, jsonDOC);
		options = new JSONObject(applyTemplate(options.toString(), jsonDOC));

		try {
			return processAction(target, topic, payload, user, options, optionalData, jsonDOC);
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

	protected String applyTemplate(String target, Object jsonDOC) {
		Matcher matcher = pattern.matcher(target);
		String result = target;
		while (matcher.find()) {
			String toReplace = matcher.group(0);
			String jPath = matcher.group(1);
			String res = "" + JsonPath.read(jsonDOC, jPath);
			String newTarget = result.replace(toReplace, res);

			result = newTarget;
		}
		OpenWareInstance.getInstance().logTrace("[TEMPLATING]Replaced before processing action:\nOld: " +	target +
												"\nNew: " +
												result);
		return result;
	}

	private String getRecursiveExtractionFromTemplate(String template, Object jsonDOC) {
		Matcher matcher = section.matcher(template);
		while (matcher.find()) {
			String toReplace = matcher.group();
			if (toReplace != null) {
				String jPath = matcher.group(1);
				String res = JsonPath.read(jsonDOC, getRecursiveExtractionFromTemplate(jPath, jsonDOC));
				template = template.replace(toReplace, res);
			}
		}

		return JsonPath.read(jsonDOC, template);

	}

	protected abstract Future<String> processAction(String target, String topic, String payload, User user,
			JSONObject options,
			OpenWareDataItem optionalData, Object optionalTemplateOptions) throws Exception;

	public abstract String getType();

	public abstract void init(JSONObject options) throws Exception;

}

package de.openinc.ow.monitoring;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.api.DataSubscriber;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.http.AlarmAPI;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

public class AlarmMonitorThreadV2 {
	private boolean RUNNING;
	private final String ALARM_TRIGGER_STATE = "ALARM_TRIGGER_STATES";
	private HashMap<String, JSONArray> alarms;
	private HashMap<String, Long> sentTS;
	private HashMap<String, Boolean> lastEventTriggered;
	private HashMap<String, OpenWareDataItem> lastSentEventItem;
	private long lastUpdate;
	private String alarmStatesPersistenceID;
	private boolean refreshing;

	public AlarmMonitorThreadV2(JSONArray alarms) {
		this.alarms = updateMonitors(alarms);
		sentTS = new HashMap<>();
		lastEventTriggered = new HashMap<String, Boolean>();
		lastSentEventItem = new HashMap<>();
		alarmStatesPersistenceID = null;
		refreshing = false;
		init();
	}

	public void init() {
		try {
			List<JSONObject> states = DataService.getGenericData(ALARM_TRIGGER_STATE, null);
			if (states.size() <= 0) {
				throw new Exception("No previous alarm trigger state found");
			}
			JSONObject lastState = states.get(0);
			alarmStatesPersistenceID = lastState.getString("_id");
			for (String key : lastState.keySet()) {
				JSONObject cState = lastState.getJSONObject(key);
				sentTS.put(key, cState.optLong("sent"));
				lastEventTriggered.put(key, cState.optBoolean("lastTriggered"));
			}
		} catch (Exception e1) {
			OpenWareInstance.getInstance().logError("Error retrieving Alarm States", e1);
		}
		DataService.addSubscription(new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				// An alarm exists... Better check if update is necessary
				refresh();
				OpenWareDataItem currentItem = item;
				OpenWareDataItem lastReceivedItem = old;

				// Looking for Alarm of item that was just received
				JSONArray alarm = alarms.get(item.getSource() + item.getId());
				if (alarm == null || alarm.length() == 0)
					return;
				// An alarm exists... Better check if update is necessary
				refresh();

				// re-check alarms to see if alarm is still there...
				alarm = alarms.get(item.getSource() + item.getId());
				if (alarm == null || alarm.length() == 0)
					return;

				for (int i = 0; i < alarm.length(); i++) {
					JSONObject currentObj = alarm.getJSONObject(i);
					String type = currentObj.getJSONObject("trigger").getString("type").toLowerCase();
					boolean lastTriggered = lastEventTriggered.getOrDefault(currentObj.getString("_id"), false);
					boolean triggered = false;

					if (type.equals("always") || type.endsWith("_always")) {
						triggered = true;
					} else if (type.startsWith("number") || type.equals("min") || type.equals("max")
							|| type.equals("min-max")) {
						triggered = checkNumeric(currentObj, currentItem, lastReceivedItem);
					} else if (type.startsWith("boolean")) {
						triggered = checkBool(currentObj, currentItem, lastReceivedItem);
					} else if (type.startsWith("string")) {
						triggered = checkString(currentObj, currentItem, lastReceivedItem);
					} else if (type.startsWith("timestamp")) {
						triggered = checkTimestamp(currentObj, currentItem, lastReceivedItem);
					}

					long checkedTS = System.currentTimeMillis();

					String ownerField = "owner";
					if (!currentObj.has("owner")) {
						ownerField = "user";
					}
					User toNotify = UserService.getInstance()
							.getUserByUID(currentObj.getJSONObject(ownerField).getString("objectId"));
					JSONObject conditions = currentObj.optJSONObject("condition");
					boolean conditionsMet = false;
					try {
						conditionsMet = conditions == null
								|| checkConditionalRule(currentObj, conditions, item, toNotify);
					} catch (Exception e) {
						OpenWareInstance.getInstance()
								.logWarn("Could not evaluate alarm conditions due to error\n" + e.getMessage());
						return;
					}

					if (triggered && conditionsMet) {
						// Alarm was triggered
						lastEventTriggered.put(currentObj.getString("_id"), true);

						boolean shouldSendAgain = (!lastTriggered || (lastTriggered
								&& checkedTS - sentTS.getOrDefault(currentObj.getString("_id"), 0l) > currentObj
										.getJSONObject("trigger").getLong("interval")));

						boolean userIsAllowed = toNotify != null && toNotify
								.canAccessRead(currentObj.getString("item_source"), currentObj.getString("item_id"));

						if (shouldSendAgain && userIsAllowed) {
							// Notification needs to be send again

							OpenWareDataItem notifyItem = currentItem.cloneItem();

							// Get the item that was created when the alarm was triggered the last time
							OpenWareDataItem lastSentAlarmItem = lastSentEventItem
									.getOrDefault(currentObj.getString("_id"), lastReceivedItem);

							if (lastSentAlarmItem != null && lastSentAlarmItem.value().size() > 0) {
								notifyItem.value().add(lastSentAlarmItem.value().get(0));
							}

							ActuatorAdapter actor = DataService
									.getActuator(currentObj.getJSONObject("action").getString("type"));
							JSONObject options = currentObj.getJSONObject("action");
							JSONObject optionsItem = new JSONObject();
							optionsItem.put("source", currentObj.get("item_source"));
							optionsItem.put("id", currentObj.get("item_id"));
							optionsItem.put("dimension", currentObj.get("item_dimension"));
							optionsItem.put("unit",
									currentItem.getValueTypes().get(currentObj.getInt("item_dimension")).getUnit());
							optionsItem.put("datetime",
									new Date(currentItem.value().get(0).getDate()).toLocaleString());
							optionsItem.put("timestamp", currentItem.value().get(0).getDate());
							optionsItem.put("value",
									currentItem.value().get(0).get(currentObj.getInt("item_dimension")).value());
							optionsItem.put("valuename",
									currentItem.getValueTypes().get(currentObj.getInt("item_dimension")).getName());
							options.put("item", optionsItem);
							options.put("trigger", currentObj.get("trigger"));
							options.put("user", toNotify.toJSON());
							options.put("end", currentItem.value().get(0).getDate());
							options.put("start", lastSentAlarmItem.value().get(0).getDate());
							if (actor != null) {
								Future result = actor.send(currentObj.getJSONObject("action").getString("target"),
										currentObj.getJSONObject("action").getString("topic"),
										currentObj.getJSONObject("action").getString("payload"), toNotify, options,
										Arrays.asList(notifyItem));

							}
							sentTS.put(currentObj.getString("_id"), checkedTS);
							lastSentEventItem.put(currentObj.getString("_id"), notifyItem);
							try {
								UserService.getInstance().notifyActiveUser(toNotify, notifyItem.toJSON());
							} catch (Exception e) {
								// Could not notify via websocket
							}

						}
					} else {
						// Alarm loest nicht mehr aus; Werte wieder normal
						lastEventTriggered.put(currentObj.getString("_id"), false);
					}

				}
			}

		});

	}

	private boolean checkConditionalRule(JSONObject alarm, JSONObject rule, OpenWareDataItem triggerItem, User user)
			throws IllegalAccessError {
		String type = rule.getString("type");
		if (type.equals("rule")) {
			String ruletype = rule.getJSONObject("rule").optString("type");
			String source = rule.getString("source");
			String id = rule.getString("id");
			if (!user.canAccessRead(source, id))
				throw new IllegalAccessError("User is not allowed to read values of " + source + "---" + id);
			OpenWareDataItem item;
			if (triggerItem.getId().equals(id) && triggerItem.getSource().equals(source)) {
				item = triggerItem;
			} else {
				item = DataService.getLiveSensorData(id, source);
			}
			Object refValue = triggerItem.value().get(0).get(alarm.getInt("item_dimension")).value();
			if (ruletype.startsWith("number")) {
				double value = (double) item.value().get(0).get(rule.getInt("dimension")).value();
				return checkNumericRule(rule.getJSONObject("rule"), value, refValue);
			}
			if (ruletype.startsWith("string")) {
				String value = (String) item.value().get(0).get(rule.getInt("dimension")).value();
				return checkStringRule(rule.getJSONObject("rule"), value, refValue);
			}
			if (ruletype.startsWith("boolean")) {
				boolean value = (boolean) item.value().get(0).get(rule.getInt("dimension")).value();
				return checkBoolRule(rule.getJSONObject("rule"), value, refValue);
			}
		}
		if (type.equals("and")) {
			JSONArray children = rule.getJSONArray("children");
			for (int i = 0; i < children.length(); i++) {
				if (!checkConditionalRule(alarm, children.getJSONObject(i), triggerItem, user))
					return false;
			}
			return true;
		}
		if (type.equals("or")) {
			JSONArray children = rule.getJSONArray("children");
			for (int i = 0; i < children.length(); i++) {
				if (checkConditionalRule(alarm, children.getJSONObject(i), triggerItem, user))
					return true;
			}
			return false;
		}
		return false;
	}

	private boolean checkNumericRule(JSONObject rule, double currentValue, Object refValue) {
		String ruleType = rule.getString("type");
		double value = rule.optDouble("value");
		double max = rule.optDouble("max");
		double min = rule.optDouble("min");
		boolean isDouble = refValue instanceof Double;

		switch (ruleType) {
		case "number_equals":
			return currentValue == value;
		case "number_equals_not":
			return currentValue != value;
		case "number_in_range":
			return currentValue > min && currentValue < max;
		case "number_out_of_range":
		case "min-max":
			return currentValue < min || currentValue > max;
		case "min":
		case "number_lt":
			return currentValue < value;
		case "max":
		case "number_gt":
			return currentValue > value;
		case "number_gt_ref":
			return isDouble && currentValue > (double) refValue;
		case "number_lt_ref":
			return isDouble && currentValue < (double) refValue;
		case "number_equals_ref":
			return isDouble && currentValue == (double) refValue;
		case "number_equals_not_ref":
			return isDouble && currentValue != (double) refValue;
		default:
			return false;
		}
	}

	private boolean checkStringRule(JSONObject rule, String currentValue, Object refValue) {
		String ruleType = rule.getString("type");
		String match = rule.optString("match");
		if (ruleType.endsWith("_ref")) {
			match = refValue.toString();
			ruleType.replace("_ref", "");
		}

		switch (ruleType) {
		case "string_equals":
		case "string-equals":
			return currentValue.equals(match);
		case "string-equals-not":
		case "string_equals_not":
			return !currentValue.equals(match);
		case "string-includes":
		case "string_includes":
			return currentValue.indexOf(match) > -1;
		case "string-includes-not":
		case "string_includes_not":
			return currentValue.indexOf(match) == -1;
		case "string-starts-with":
		case "string_starts_with":
			return currentValue.startsWith(match);
		case "string-starts-with-not":
		case "string_starts_with_not":
			return !currentValue.startsWith(match);
		case "string-ends-with":
		case "string_ends_with":
			return currentValue.endsWith(match);
		case "string-ends-with-not":
		case "string_ends_with_not":
			return !currentValue.endsWith(match);
		default:
			return false;
		}
	}

	private boolean checkBoolRule(JSONObject rule, boolean currentValue, Object refValue) {
		String ruleType = rule.getString("type");
		boolean isBool = refValue instanceof Boolean;

		switch (ruleType) {
		case "boolean_true":
			return currentValue;
		case "boolean_false":
			return !currentValue;
		case "boolean_equals_ref":
			return isBool && currentValue == (boolean) refValue;
		case "boolean_equals_not_ref":
			return isBool && currentValue != (boolean) refValue;
		default:
			return false;
		}
	}

	private boolean checkNumeric(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		int dimension = currentObj.getInt("item_dimension");
		double currentValue = (double) currentItem.value().get(0).get(dimension).value();
		double lastValue = Double.MAX_VALUE;

		if (lastItem != null)
			lastValue = (double) lastItem.value().get(0).get(dimension).value();

		double value = currentObj.getJSONObject("trigger").has("value")
				&& !currentObj.getJSONObject("trigger").get("value").equals(JSONObject.NULL)
						? currentObj.getJSONObject("trigger").getDouble("value")
						: Double.MIN_VALUE;

		double min = currentObj.getJSONObject("trigger").has("min")
				&& !currentObj.getJSONObject("trigger").get("min").equals(JSONObject.NULL)
						? currentObj.getJSONObject("trigger").getDouble("min")
						: Double.MIN_VALUE;

		double max = currentObj.getJSONObject("trigger").has("max")
				&& !currentObj.getJSONObject("trigger").get("max").equals(JSONObject.NULL)
						? currentObj.getJSONObject("trigger").getDouble("max")
						: Double.MAX_VALUE;

		switch (currentObj.getJSONObject("trigger").getString("type")) {
		case "number_change":
			return currentValue != lastValue;
		case "number_equals":
			return currentValue == value;
		case "number_equals_not":
			return currentValue != value;
		case "number_in_range":
			return currentValue > min && currentValue < max;
		case "number_out_of_range":
		case "min-max":
			return currentValue < min || currentValue > max;
		case "min":
		case "number_lt":
			return currentValue < value;
		case "max":
		case "number_gt":
			return currentValue > value;
		default:
			return false;
		}
	}

	private boolean checkString(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		int dimension = currentObj.getInt("item_dimension");
		if (!currentObj.getJSONObject("trigger").has("string_match"))
			return false;

		String currentValue = currentItem.value().get(0).get(dimension).value().toString().toLowerCase();
		String lastValue = "";
		if (lastItem != null) {
			lastValue = lastItem.value().get(0).get(dimension).value().toString().toLowerCase();
		}

		String match = currentObj.optString("string_match").toLowerCase();

		switch (currentObj.getJSONObject("trigger").getString("type")) {
		case "string_change":
		case "string-change":
			return !currentValue.equals(lastValue);
		case "string_equals":
		case "string-equals":
			return currentValue.equals(match);
		case "string-equals-not":
		case "string_equals_not":
			return !currentValue.equals(match);
		case "string-includes":
		case "string_includes":
			return currentValue.indexOf(match) > -1;
		case "string-includes-not":
		case "string_includes_not":
			return currentValue.indexOf(match) == -1;
		case "string-starts-with":
		case "string_starts_with":
			return currentValue.startsWith(match);
		case "string-starts-with-not":
		case "string_starts_with_not":
			return !currentValue.startsWith(match);
		case "string-ends-with":
		case "string_ends_with":
			return currentValue.endsWith(match);
		case "string-ends-with-not":
		case "string_ends_with_not":
			return !currentValue.endsWith(match);
		default:
			return false;
		}
	}

	private boolean checkBool(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		int dimension = currentObj.getInt("item_dimension");
		boolean currentValue;
		boolean lastValue;

		try {
			currentValue = (boolean) currentItem.value().get(0).get(dimension).value();
			lastValue = !currentValue;
			if (lastItem != null)
				lastValue = (boolean) lastItem.value().get(0).get(dimension).value();
		} catch (ClassCastException e) {
			return false;
		}

		switch (currentObj.getJSONObject("trigger").getString("type")) {
		case "boolean_true":
			return currentValue;
		case "boolean_false":
			return !currentValue;
		case "boolean-rising-edge":
		case "boolean_rising_edge":
			return !lastValue && currentValue;
		case "boolean-falling-edge":
		case "boolean_falling_edge":
			return lastValue && !currentValue;
		case "boolean_change":
		case "boolean-rising-falling-edge":
			return lastValue != currentValue;
		default:
			return false;
		}
	}

	private boolean checkTimestamp(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		Instant currTS = Instant.ofEpochMilli(currentItem.value().get(0).getDate());
		ZonedDateTime zdt = ZonedDateTime.ofInstant(currTS, ZoneId.of(currentObj.getString("timezone")));

		// TODO Auto-generated method stub
		return false;
	}

	public HashMap<String, JSONArray> updateMonitors(JSONArray alarms) {
		lastUpdate = System.currentTimeMillis();
		HashMap<String, JSONArray> newAlarms = new HashMap<String, JSONArray>();

		for (int i = 0; i < alarms.length(); i++) {
			JSONObject current = alarms.getJSONObject(i);
			String item_source = current.getString("item_source");
			String item_id = current.getString("item_id");
			String ownerField = "owner";
			if (!current.has("owner")) {
				ownerField = "user";
			}
			User toNotify = UserService.getInstance()
					.getUserByUID(current.getJSONObject(ownerField).getString("objectId"));
			boolean userIsAllowed = toNotify != null && toNotify.canAccessRead(item_source, item_id);
			if (!Config.getBool("accessControl", true) || userIsAllowed) {
				String tempIndex = item_source + item_id;
				JSONArray idAlarms = newAlarms.getOrDefault(tempIndex, new JSONArray());
				idAlarms.put(current);
				newAlarms.put(tempIndex, idAlarms);
			}
		}
		this.alarms = newAlarms;
		return this.alarms;
	}

	public HashMap<String, JSONArray> getCurrentAlarms() {
		return this.alarms;
	}

	public void refresh() {
		refresh(false);
	}

	public void refresh(boolean force) {
		if (refreshing)
			return;
		refreshing = true;
		if (System.currentTimeMillis() - lastUpdate < 30000 && !force) {
			refreshing = false;
			return;
		}
		OpenWareInstance.getInstance().logDebug("Refreshing alarms...");
		List<JSONObject> alarmsCheck = null;
		try {
			alarmsCheck = DataService.getGenericData(AlarmAPI.ALARMSV2, null);
			JSONArray update = new JSONArray();
			for (JSONObject o : alarmsCheck) {
				update.put(o);
			}
			updateMonitors(update);
		} catch (Exception e) {
			OpenWareInstance.getInstance().logWarn("Could not refresh alarms");
		}

		if (alarmsCheck != null) {
			try {
				JSONObject state = new JSONObject();
				for (JSONObject o : alarmsCheck) {
					JSONObject cState = new JSONObject();
					cState.put("sent", sentTS.getOrDefault(o.getString("_id"), 0l));
					cState.put("lastTriggered", lastEventTriggered.getOrDefault(o.getString("_id"), false));
					state.put(o.getString("_id"), cState);
				}

				alarmStatesPersistenceID = DataService.storeGenericData(ALARM_TRIGGER_STATE, alarmStatesPersistenceID,
						state);
			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Could not store alarm states", e);
			}
		}
		refreshing = false;
	}

	/*
	 * private void processPush() { UserService userService =
	 * UserService.getInstance(); HashMap<JSONObject, OpenWareDataItem> temp = new
	 * HashMap<>(); for (JSONObject key : temp.keySet()) { JSONObject extras = new
	 * JSONObject(); extras.put("alarm", key); extras.put("sensorInfo",
	 * temp.get(key).toJSON()); JSONObject message = new JSONObject();
	 * message.put("title", temp.get(key).getMeta().optString("alarmTitle"));
	 * message.put("message", temp.get(key).getMeta().optString("alarmText"));
	 * message.put("extras", extras);
	 * userService.sendNotification(UserService.getInstance().getUserByUsername((
	 * "toNotify")), message); }
	 * 
	 * }
	 */

}

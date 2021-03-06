package de.openinc.ow.monitoring;

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
	private HashMap<String, JSONArray> alarms;
	private HashMap<String, Long> sentTS;
	private HashMap<String, Boolean> lastEventTriggered;
	private long lastUpdate;

	public AlarmMonitorThreadV2(JSONArray alarms) {
		this.alarms = updateMonitors(alarms);
		sentTS = new HashMap<>();
		lastEventTriggered = new HashMap<>();
		init();
	}

	public void init() {
		DataService.addSubscription(new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {

				OpenWareDataItem currentItem = (OpenWareDataItem) item;
				OpenWareDataItem lastItem = old;

				//Looking for Alarm of item that was just received
				JSONArray alarm = alarms.get(item.getUser() + item.getId());
				if (alarm == null || alarm.length() == 0)
					return;
				//An alarm exists... Better check if update is necessary
				refresh();

				// re-check alarms to see if alarm is still there...
				alarm = alarms.get(item.getUser() + item.getId());
				if (alarm == null || alarm.length() == 0)
					return;

				OpenWareInstance.getInstance().logTrace("Checking Alarms triggered: " + item.getUser() +
														"." +
														item.getId());
				for (int i = 0; i < alarm.length(); i++) {
					JSONObject currentObj = alarm.getJSONObject(i);
					String type = currentObj.getJSONObject("trigger").getString("type").toLowerCase();
					boolean lastTriggered = lastEventTriggered
							.getOrDefault(currentObj.getString("_id"), false);
					boolean triggered = false;

					if (type.equals("always") || type.endsWith("_always")) {
						triggered = true;
					} else if (type.startsWith("number") || type.equals("min") || type.equals("max")
							|| type.equals("min-max")) {
						triggered = checkNumeric(currentObj, currentItem, lastItem);
					} else if (type.startsWith("boolean")) {
						triggered = checkBool(currentObj, currentItem, lastItem);
					} else if (type.startsWith("string")) {
						triggered = checkString(currentObj, currentItem, lastItem);
					}

					long checkedTS = System.currentTimeMillis();

					if (triggered) {
						// Alarm was triggered
						lastEventTriggered.put(currentObj.getString("_id"), true);
						if (currentObj.optBoolean("save")) {
							JSONObject meta = new JSONObject();
							meta.put("alarm", true);
							OpenWareDataItem alarmItem = currentItem.cloneItem();
							alarmItem.setId(currentObj.getString("_id"));
							alarmItem.setName(currentItem.getName() +	" (Alarm " +
												currentObj.getString("_id"));
							alarmItem.value(currentItem.value());
							DataService.onNewData(alarmItem);
						}

						boolean shouldSendAgain = (!lastTriggered
								|| (lastTriggered && checkedTS
										- sentTS.getOrDefault(currentObj.getString("_id"), 0l) > currentObj
												.getJSONObject("trigger").getLong("interval")));

						User toNotify = UserService.getInstance()
								.getUserByUID(currentObj.getJSONObject("owner").getString("objectId"));
						boolean userIsAllowed = toNotify != null && toNotify
								.canAccessRead(currentObj.getString("item_source"), currentObj.getString("item_id"));

						if (shouldSendAgain && userIsAllowed) {

							// Notification needs to be send again
							String text = "Alarm " +	currentObj.optString("name") +
											" Sensor " +
											currentItem.getName() +
											" :\n" +
											"\nErfasste Werte:\n" +
											currentItem.getValueTypes().get(currentObj.getInt("item_dimension"))
													.getName() +
											": " +
											currentItem.value().get(0).get(currentObj.getInt("item_dimension"))
													.value() +
											" " +
											currentItem.getValueTypes().get(currentObj.getInt("item_dimension"))
													.getUnit() +
											"\nZeitpunkt:" +
											new Date(currentItem.value().get(0).getDate()).toLocaleString();
							;
							OpenWareDataItem notifyItem = currentItem.cloneItem();
							notifyItem.value(currentItem.value());

							String title = "Alarm " +	currentObj.optString("name") +
											" (" +
											notifyItem.getName() +
											")";
							notifyItem.getMeta().put("alarmTitle", title);
							notifyItem.getMeta().put("alarmText", text);

							ActuatorAdapter actor = DataService
									.getActuator(currentObj.getJSONObject("action").getString("type"));
							JSONObject options = currentObj.getJSONObject("action");
							JSONObject optionsItem = new JSONObject();
							optionsItem.put("source", currentObj.get("item_source"));
							optionsItem.put("id", currentObj.get("item_id"));
							optionsItem.put("dimension", currentObj.get("item_dimension"));
							optionsItem.put("unit", currentItem.getValueTypes().get(currentObj.getInt("item_dimension"))
									.getUnit());
							optionsItem.put("datetime",
									new Date(currentItem.value().get(0).getDate()).toLocaleString());
							optionsItem.put("timestamp", currentItem.value().get(0).getDate());
							optionsItem.put("value",
									currentItem.value().get(0).get(currentObj.getInt("item_dimension")).value());
							optionsItem.put("valuename",
									currentItem.getValueTypes().get(currentObj.getInt("item_dimension"))
											.getName());
							options.put("item", optionsItem);
							options.put("trigger", currentObj.get("trigger"));
							options.put("user", toNotify.toJSON());
							if (actor != null) {
								Future result = actor.send(currentObj.getJSONObject("action").getString("target"),
										currentObj.getJSONObject("action").getString("topic"),
										currentObj.getJSONObject("action").getString("payload"),
										toNotify,
										options, notifyItem);

							}

							sentTS.put(currentObj.getString("_id"), checkedTS);
						}
					} else {
						// Alarm loest nicht mehr aus; Werte wieder normal
						lastEventTriggered.put(currentObj.getString("_id"), false);
					}

				}
			}

		});

		/*
		RUNNING = true;
		
		while (RUNNING) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		OpenWareInstance.getInstance().logError("Killing AlarmMonitorThread");
		*/
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
			return currentValue < min;
		case "max":
		case "number_gt":
			return currentValue > max;
		default:
			return false;
		}
	}

	private boolean checkString(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		int dimension = currentObj.getInt("item_dimension");
		if (!currentObj.getJSONObject("trigger").has("string_match"))
			return false;

		String currentValue = currentItem.value().get(0).get(dimension).value().toString()
				.toLowerCase();
		String lastValue = "";
		if (lastItem != null) {
			lastValue = lastItem.value().get(0).get(dimension).value().toString()
					.toLowerCase();
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

	public HashMap<String, JSONArray> updateMonitors(JSONArray alarms) {
		lastUpdate = System.currentTimeMillis();
		HashMap<String, JSONArray> newAlarms = new HashMap<String, JSONArray>();

		for (int i = 0; i < alarms.length(); i++) {
			JSONObject current = alarms.getJSONObject(i);
			String item_source = current.getString("item_source");
			String item_id = current.getString("item_id");
			User toNotify = UserService.getInstance()
					.getUserByUID(current.getJSONObject("owner").getString("objectId"));
			boolean userIsAllowed = toNotify != null && toNotify.canAccessRead(item_source, item_id);
			if (!Config.accessControl || userIsAllowed) {
				String tempIndex = item_source + item_id;
				JSONArray idAlarms = newAlarms.getOrDefault(tempIndex, new JSONArray());
				idAlarms.put(current);
				newAlarms.put(tempIndex, idAlarms);
			}
		}
		this.alarms = newAlarms;
		return this.alarms;
	}

	private void refresh() {
		if (System.currentTimeMillis() - lastUpdate < 30000)
			return;
		OpenWareInstance.getInstance().logDebug("Refreshing alarms...");
		List<JSONObject> alarmsCheck;
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

	}

	/*
		private void processPush() {
			UserService userService = UserService.getInstance();
			HashMap<JSONObject, OpenWareDataItem> temp = new HashMap<>();
			for (JSONObject key : temp.keySet()) {
				JSONObject extras = new JSONObject();
				extras.put("alarm", key);
				extras.put("sensorInfo", temp.get(key).toJSON());
				JSONObject message = new JSONObject();
				message.put("title", temp.get(key).getMeta().optString("alarmTitle"));
				message.put("message", temp.get(key).getMeta().optString("alarmText"));
				message.put("extras", extras);
				userService.sendNotification(UserService.getInstance().getUserByUsername(("toNotify")), message);
			}
	
		}
	*/

}

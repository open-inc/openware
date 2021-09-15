package de.openinc.ow.monitoring;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.mail.EmailException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.DataSubscriber;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.middleware.io.MailSender;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

public class AlarmMonitorThreadV1 extends Thread {
	private boolean RUNNING;
	private HashMap<String, JSONArray> alarms;
	private HashMap<JSONObject, OpenWareDataItem> pushProcessQueue;
	private HashMap<JSONObject, OpenWareDataItem> mailProcessQueue;
	private HashMap<JSONObject, OpenWareDataItem> ticketProcessQueue;
	private HashMap<String, Long> sentTS;
	private HashMap<String, Boolean> lastEventTriggered;
	private MailSender ms;

	public AlarmMonitorThreadV1(JSONArray alarms) {
		ms = MailSender.getInstance();
		this.alarms = updateMonitors(alarms);
		mailProcessQueue = new HashMap<>();
		ticketProcessQueue = new HashMap<>();
		pushProcessQueue = new HashMap<>();
		sentTS = new HashMap<>();
		lastEventTriggered = new HashMap<>();

	}

	@Override
	public void run() {
		DataService.addSubscription(new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				OpenWareDataItem currentItem = item;
				OpenWareDataItem lastItem = old;

				JSONArray alarm = alarms.get(item.getUser() + item.getId());
				if (alarm != null && alarm.length() > 0) {
					// Store all checked boolean indexes
					for (int i = 0; i < alarm.length(); i++) {
						JSONObject currentObj = alarm.getJSONObject(i);
						String type = currentObj.getString("type").toLowerCase();
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

						boolean lastEventTriggeredAlso = lastEventTriggered
								.getOrDefault(currentObj.getString("alarmid"), false);

						long checkedTS = System.currentTimeMillis();

						// Alarm was triggered
						if (triggered) {
							lastEventTriggered.put(currentObj.getString("alarmid"), true);
							if (currentObj.getBoolean("save")) {
								JSONObject meta = new JSONObject();
								meta.put("alarm", true);
								OpenWareDataItem alarmItem = currentItem.cloneItem();
								alarmItem.setId(currentItem.getId() +	"." +
												currentObj.getString("alarmid"));
								alarmItem.setName("Alarm-" + currentObj.optString("name"));
								alarmItem.value(currentItem.value());
								// DataService.onNewData(alarmItem);
							}

							boolean shouldSendAgain = (!lastEventTriggeredAlso
									|| (lastEventTriggeredAlso && checkedTS
											- sentTS.getOrDefault(currentObj.getString("alarmid"), 0l) > currentObj
													.getLong("interval")));
							// Notification needs to be send again
							if (shouldSendAgain) {
								String text = "Alarm " +	currentObj.optString("name") +
												" Sensor " +
												currentItem.getName() +
												" :\n" +
												"\nErfasste Werte:\n" +
												currentItem.getValueTypes().get(currentObj.getInt("dimension"))
														.getName() +
												": " +
												currentItem.value().get(0).get(currentObj.getInt("dimension"))
														.value() +
												" " +
												currentItem.getValueTypes().get(currentObj.getInt("dimension"))
														.getUnit() +
												"\nZeitpunkt:" +
												new Date(currentItem.value().get(0).getDate()).toLocaleString();
								;
								OpenWareDataItem notifyItem = currentItem.cloneItem();
								notifyItem.value(currentItem.value());

								String title = "Alarm - " + currentObj.optString("name");
								notifyItem.getMeta().put("alarmTitle", title);
								notifyItem.getMeta().put("alarmText", text);

								Map<JSONObject, OpenWareDataItem> relevantQueue = null;
								switch (currentObj.getString("notificationType")) {
								case "mail":
									relevantQueue = mailProcessQueue;
									break;
								case "ticket":
									relevantQueue = ticketProcessQueue;
									break;
								case "push":
									relevantQueue = pushProcessQueue;
									break;
								default:
									break;
								}

								if (relevantQueue != null)
									relevantQueue.put(currentObj, notifyItem);
								sentTS.put(currentObj.getString("alarmid"), checkedTS);
							}
						} else {
							// Alarm l�st nicht mehr aus; Werte wieder normal
							lastEventTriggered.put(currentObj.getString("alarmid"), false);
						}

					}
				}

			}

		});

		RUNNING = true;

		while (RUNNING)

		{
			processMail();
			//processTickets();
			processPush();
			try {
				Thread.sleep(1000);
			} catch (

			InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		OpenWareInstance.getInstance().logError("Killing AlarmMonitorThread");
	}

	public void stopThread() {
		this.RUNNING = false;
	}

	private boolean checkNumeric(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		double currentValue = (double) currentItem.value().get(0).get(currentObj.getInt("dimension")).value();
		double lastValue = Double.MAX_VALUE;

		if (lastItem != null)
			lastValue = (double) lastItem.value().get(0).get(currentObj.getInt("dimension")).value();

		double value = currentObj.has("value") && !currentObj.get("value").equals(JSONObject.NULL)
				? currentObj.getDouble("value")
				: Double.MIN_VALUE;

		double min = currentObj.has("min") && !currentObj.get("min").equals(JSONObject.NULL)
				? currentObj.getDouble("min")
				: Double.MIN_VALUE;

		double max = currentObj.has("max") && !currentObj.get("max").equals(JSONObject.NULL)
				? currentObj.getDouble("max")
				: Double.MAX_VALUE;

		switch (currentObj.getString("type")) {
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
		if (!currentObj.has("string_match"))
			return false;

		String currentValue = currentItem.value().get(0).get(currentObj.getInt("dimension")).value().toString()
				.toLowerCase();
		String lastValue = "";
		if (lastItem != null) {
			lastValue = lastItem.value().get(0).get(currentObj.getInt("dimension")).value().toString()
					.toLowerCase();
		}

		String match = currentObj.optString("string_match").toLowerCase();

		switch (currentObj.getString("type")) {
		case "string_change":
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
		boolean currentValue;
		boolean lastValue;

		try {
			currentValue = (boolean) currentItem.value().get(0).get(currentObj.getInt("dimension")).value();
			lastValue = !currentValue;
			if (lastItem != null)
				lastValue = (boolean) lastItem.value().get(0).get(currentObj.getInt("dimension")).value();
		} catch (ClassCastException e) {
			return false;
		}

		switch (currentObj.getString("type")) {
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
		this.alarms = new HashMap<String, JSONArray>();
		for (int i = 0; i < alarms.length(); i++) {
			JSONObject current = alarms.getJSONObject(i);
			String item_source = current.getString("user");
			String item_id = current.getString("sensorid");
			User toNotify = UserService.getInstance().getUserByUsername(current.getString("toNotify"));
			boolean userIsAllowed = toNotify != null && toNotify.canAccessRead(item_source, item_id);
			if (!Config.getBool("accessControl", true) || userIsAllowed) {
				String tempIndex = item_source + item_id;
				JSONArray idAlarms = this.alarms.getOrDefault(tempIndex, new JSONArray());
				idAlarms.put(current);
				this.alarms.put(tempIndex, idAlarms);
			}
		}
		return this.alarms;
	}

	private void processPush() {
		UserService userService = UserService.getInstance();
		HashMap<JSONObject, OpenWareDataItem> temp = new HashMap<>();
		temp.putAll(pushProcessQueue);
		pushProcessQueue.clear();
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

	private void processMail() {
		HashMap<JSONObject, OpenWareDataItem> temp = new HashMap<>();
		temp.putAll(mailProcessQueue);
		mailProcessQueue.clear();
		for (JSONObject key : temp.keySet()) {
			String subject = temp.get(key).getMeta().optString("alarmTitle");
			String message = temp.get(key).getMeta().optString("alarmText");
			try {
				ms.sendMail(Config.get("mailserverUser","user"), key.getString("mail"), subject, message);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (EmailException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}

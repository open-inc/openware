package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.post;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.commons.mail.EmailException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;

import de.openinc.api.DataSubscriber;
import de.openinc.api.OpenWareAPI;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.helper.HTTPResponseHelper;
import de.openinc.ow.core.helper.UsernameConverter;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.sender.MailSender;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

public class AlarmAPI implements OpenWareAPI {
	public static final String ALARMS = "alarms";

	/**
	 * API constant to modify/create Alarm events
	 */
	public static final String ALARM_EVENT_API = "/alarms";

	/**
	 * API constant to get registered Alarm events
	 */
	public static final String ALARM_EVENT_GET_API = "/alarms/:userid";
	/**
	 * API constant to delete registered Alarm events
	 */
	public static final String ALARM_EVENT_DELETE_API = "/alarms/:userid/:alarmid";

	private JSONArray initialAlarms;
	private AlarmMonitorThread amt;
	protected static AlarmAPI as;

	public static AlarmAPI getInstance() {
		if (as == null) {
			as = new AlarmAPI();
		}
		return as;
	}

	private AlarmAPI() {

		OpenWareInstance.getInstance().logInfo("Starting AlarmService");
		try {
			String[] alarms = DataService.getGenericData(ALARMS, null);
			initialAlarms = new JSONArray();
			for (String alarm : alarms) {
				initialAlarms.put(new JSONObject(alarm));
			}
			amt = new AlarmMonitorThread(initialAlarms);
			amt.start();

			OpenWareInstance.getInstance().logInfo("Started AlarmService");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Could not Start Alarm Service", e);
		}

	}

	private String registerAlarm(User user, JSONObject parameter) {

		if (parameter != null) {
			if (parameter.has("toNotify") && parameter.has("sensorid") && parameter.has("user")
					&& (parameter.has("mail") || parameter.has("notificationType")) && parameter.has("interval")
					&& parameter.has("save")) {
				if (user == null || user.canAccessRead(parameter.getString("user"), parameter.getString("sensorid"))) {
					String id = "alarm" + System.currentTimeMillis();
					parameter.put("alarmid", id);
					DataService.storeGenericData(ALARMS, id, parameter.toString());
					initialAlarms.put(parameter);
					amt.updateMonitors(initialAlarms);
					return id;
				}
			}
		}
		return null;
	}

	private boolean deleteAlarm(String userID, String alarmid) {
		if (userID != null && alarmid != null) {
			String[] data = DataService.getGenericData(ALARMS, alarmid);
			if (data != null && data.length > 0) {
				JSONObject alarm = new JSONObject(data[0]);
				if (alarm.getString("toNotify").equals(UsernameConverter.getUsername(userID))) {
					DataService.removeGenericData(ALARMS, alarmid);
					String[] alarms = DataService.getGenericData(ALARMS, null);
					initialAlarms = new JSONArray();
					for (String alarm2 : alarms) {
						initialAlarms.put(new JSONObject(alarm2));
					}
					amt.updateMonitors(initialAlarms);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void registerRoutes() {
		post(ALARM_EVENT_GET_API, (req, res) -> {
			String userID = req.params("userid");
			User user = req.session().attribute("user");
			JSONObject parameter = new JSONObject(req.body());

			if (Config.accessControl) {
				boolean canRead = userID.equals(user.getName());
				if (!canRead) {
					halt(403, "You are not allowed to add the alarm for this user");
				}

				parameter.put("toNotify", userID);
				String success = registerAlarm(req.session().attribute("user"), parameter);
				if (success != null) {
					JSONObject result = new JSONObject();
					result.put("message", "Succesfully created Alarm");
					result.put("id", success);
					return HTTPResponseHelper.generateResponse(res, 200, result, null);
				} else {
					JSONObject result = new JSONObject();
					result.put("message", "Could not create Alarm");
					return HTTPResponseHelper.generateResponse(res, 300, null, result);
				}
			} else {
				parameter.put("toNotify", userID);
				String success = registerAlarm(user, parameter);
				if (success != null) {
					JSONObject result = new JSONObject();
					result.put("message", "Succesfully created Alarm");
					result.put("id", success);
					return HTTPResponseHelper.generateResponse(res, 200, result, null);
				} else {
					JSONObject result = new JSONObject();
					result.put("message", "Could not create Alarm");
					return HTTPResponseHelper.generateResponse(res, 300, null, result);
				}
			}

		});

		delete(ALARM_EVENT_DELETE_API, (req, res) -> {

			String userID = req.params("userid");
			String alarmid = req.params("alarmid");
			if (Config.accessControl) {
				User user = req.session().attribute("user");
				if (user == null || !user.getName().equals(userID))
					halt(403, "You are not allowed to delete the alarm");
			}
			if (deleteAlarm(userID, alarmid)) {
				res.status(200);
				return "Successfully deleted alarm!";
			} else {
				res.status(300);
				return "You must provide a user and alarm id";

			}

		});
		get(ALARM_EVENT_GET_API, (req, res) -> {
			String userID = req.params("userid");
			if (Config.accessControl) {
				User user = req.session().attribute("user");
				boolean canRead = userID.equals(user.getName());
				if (!canRead) {
					halt(403, "You are not allowed to read the alarms");
				}

			}
			JSONArray userAlarms = new JSONArray();
			for (int i = 0; i < initialAlarms.length(); i++) {
				JSONObject current = initialAlarms.getJSONObject(i);
				if (current.getString("toNotify").equals(userID)) {
					userAlarms.put(current);
				}
			}
			res.status(200);
			return userAlarms;
		});

	}

}

class AlarmMonitorThread extends Thread {
	private boolean RUNNING;
	private HashMap<String, JSONArray> alarms;
	private HashMap<JSONObject, OpenWareDataItem> pushProcessQueue;
	private HashMap<JSONObject, OpenWareDataItem> mailProcessQueue;
	private HashMap<JSONObject, OpenWareDataItem> ticketProcessQueue;
	private HashMap<String, Long> sentTS;
	private HashMap<String, Boolean> lastEventTriggered;
	private HashMap<String, OpenWareDataItem> lastDataItemReceived;
	private MailSender ms;

	public AlarmMonitorThread(JSONArray alarms) {
		ms = MailSender.getInstance(Config.outboundMailServer, Config.outboundMailServerPort, Config.mailserverUser,
				Config.mailserverPassword);
		this.alarms = updateMonitors(alarms);
		mailProcessQueue = new HashMap<>();
		ticketProcessQueue = new HashMap<>();
		pushProcessQueue = new HashMap<>();
		sentTS = new HashMap<>();
		lastEventTriggered = new HashMap<>();
		lastDataItemReceived = new HashMap<>();

	}

	@Override
	public void run() {
		DataService.addSubscription(new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem item) throws Exception {
				if (item instanceof OpenWareDataItem) {
					OpenWareDataItem currentItem = (OpenWareDataItem) item;
					OpenWareDataItem lastItem = (OpenWareDataItem) lastDataItemReceived
							.get(currentItem.getUser() + currentItem.getId());

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
									alarmItem.setId(currentItem.getId() + "." + currentObj.getString("alarmid"));
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
									String text = "Alarm " + currentObj.optString("name") + " Sensor "
											+ currentItem.getName() + " :\n" + "\nErfasste Werte:\n"
											+ currentItem.getValueTypes().get(currentObj.getInt("dimension")).getName()
											+ ": "
											+ currentItem.value().get(0).get(currentObj.getInt("dimension")).value()
											+ " "
											+ currentItem.getValueTypes().get(currentObj.getInt("dimension")).getUnit()
											+ "\nZeitpunkt:"
											+ new Date(currentItem.value().get(0).getDate()).toLocaleString();
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
						// Update Bools and clear list
						lastDataItemReceived.put(currentItem.getUser() + currentItem.getId(), currentItem);
					}

				}

			}

		});

		RUNNING = true;

		while (RUNNING)

		{
			processMail();
			processTickets();
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
		AlarmAPI.as = null;
	}

	public void stopThread() {
		this.RUNNING = false;
	}

	private boolean checkNumeric(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		double currentValue = (double) currentItem.value().get(0).get(currentObj.getInt("dimension")).value();
		double lastValue = (double) lastItem.value().get(0).get(currentObj.getInt("dimension")).value();

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
		String lastValue = lastItem.value().get(0).get(currentObj.getInt("dimension")).value().toString().toLowerCase();
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
			String cUser = current.getString("user");
			String cSensor = current.getString("sensorid");
			User toNotify = UserService.getInstance().getUser(current.getString("toNotify"));
			boolean userIsAllowed = toNotify != null && toNotify.canAccessRead(cUser, cSensor);
			if (!Config.accessControl || userIsAllowed) {
				String tempIndex = cUser + cSensor;
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
			userService.sendNotification(UserService.getInstance().getUser(("toNotify")), message);
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
				ms.sendMail(Config.mailserverUser, key.getString("mail"), subject, message);
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

	private void processTickets() {
		if (Config.ticketEnabled) {
			String HOST = Config.ticketHost + ":" + Config.ticketPort;
			HashMap<JSONObject, OpenWareDataItem> temp = new HashMap<>();
			temp.putAll(ticketProcessQueue);
			ticketProcessQueue.clear();
			for (JSONObject key : temp.keySet()) {
				String subject = temp.get(key).getMeta().optString("alarmTitle");
				String message = temp.get(key).getMeta().optString("alarmText");
				String apiKey = temp.get(key).getMeta().optString("ticketUser");
				if (apiKey.equals("")) {
					apiKey = Config.ticketAccessToken;
				}
				try {
					Future<HttpResponse<JsonNode>> res;
					// HttpResponse<JsonNode> res;
					HashMap<String, String> headers = new HashMap<>();
					headers.put("accesstoken", apiKey);
					headers.put("Content-Type", "application/json");
					JSONObject ticket = new JSONObject();
					ticket.put("subject", subject);
					ticket.put("issue", message);
					ticket.put("owner", Config.ticketUser);
					ticket.put("group", Config.ticketWorkerGroup);
					ticket.put("type", Config.ticketType);
					ticket.put("priority", 1);
					try {
						res = Unirest.post(HOST + "/api/v1/tickets/create").headers(headers).body(ticket)
								.asJsonAsync(new Callback<JsonNode>() {

									@Override
									public void failed(UnirestException e) {
										// TODO Auto-generated method stub

									}

									@Override
									public void completed(HttpResponse<JsonNode> response) {
										JSONObject ticketData = response.getBody().getObject();
										if (ticketData.optBoolean("success")) {
											key.put("ticketUID", ticketData.optJSONObject("ticket").opt("uid"));
											DataService.storeGenericData(AlarmAPI.ALARMS, key.getString("alarmid"),
													key.toString());
										}

									}

									@Override
									public void cancelled() {
										// TODO Auto-generated method stub

									}
								});

						// JSONObject response = res.get().getBody().getObject();
						// JSONObject response = res.getBody().getObject();
						// OpenWareInstance.getInstance().logDebug(response.toString());

					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}
}

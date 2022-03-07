package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.monitoring.AlarmMonitorThreadV1;
import de.openinc.ow.monitoring.AlarmMonitorThreadV2;

public class AlarmAPI implements OpenWareAPI {
	public static final String ALARMS = "alarms";
	public static final String ALARMSV2 = "alarmsV2";

	/**
	 * API constant to modify/create Alarm events
	 */
	public static final String ALARM_EVENT_API = "/alarms";
	public static final String ALARM_EVENT_APIV2 = "/alarmsV2/";

	/**
	 * API constant to get registered Alarm events
	 */
	public static final String ALARM_EVENT_GET_API = "/alarms/:userid";
	public static final String ALARM_EVENT_GET_APIV2 = "/alarmsV2/:userid";
	/**
	 * API constant to delete registered Alarm events
	 */
	public static final String ALARM_EVENT_DELETE_API = "/alarms/:userid/:alarmid";
	public static final String ALARM_EVENT_DELETE_APIV2 = "/alarmsV2/:userid/:alarmid";

	private JSONArray initialAlarms;
	private JSONArray initialAlarmsV2;
	private AlarmMonitorThreadV1 amt;
	private AlarmMonitorThreadV2 amt2;
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
			List<JSONObject> alarms = DataService.getGenericData(ALARMS, null);
			List<JSONObject> alarms2 = DataService.getGenericData(ALARMSV2, null);

			initialAlarms = new JSONArray();
			for (JSONObject alarm : alarms) {
				initialAlarms.put(alarm);
			}
			amt = new AlarmMonitorThreadV1(initialAlarms);
			amt.start();

			initialAlarmsV2 = new JSONArray();
			for (JSONObject alarm : alarms2) {
				initialAlarmsV2.put(alarm);
			}
			amt2 = new AlarmMonitorThreadV2(initialAlarmsV2);

			OpenWareInstance.getInstance().logInfo("Started AlarmServices V1&V2");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Could not Start Alarm Service", e);
		}

	}

	private String registerAlarmV1(User user, JSONObject parameter)
			throws IllegalArgumentException, IllegalAccessError {
		/*
		if (parameter == null) {
			throw new IllegalArgumentException(
					"Could not create Alarm due to missing all of the following parameters:\n trigger, action, item_source, item_id, item_dimension, owner");
		}
		if (!(parameter.has("toNotify") && parameter.has("sensorid") && parameter.has("user")
				&& (parameter.has("mail") || parameter.has("notificationType")) && parameter.has("interval")
				&& parameter.has("save"))) {
			//Missing Parameters
			throw new IllegalArgumentException(
					"API is deprecated and will not be maintained! Please use new AlarmV2 API");
		}
		
		if ((user == null
				|| !(user.canAccessRead(parameter.getString("user"), parameter.getString("sensorid"))))) {
			//Not authorized
			throw new IllegalAccessError("Not authorized to create alarm for this data source / sensor");
		
		}
		
		String id = "alarm" + System.currentTimeMillis();
		parameter.put("alarmid", id);
		DataService.storeGenericData(ALARMS, id, parameter.toString());
		initialAlarms.put(parameter);
		amt.updateMonitors(initialAlarms);
		return id;
		 */
		throw new IllegalAccessError("Please Use AlarmV2 API");
	}

	private boolean deleteAlarm(User user, String alarmid) {
		if (user != null && alarmid != null) {
			try {
				List<JSONObject> data = DataService.getGenericData(ALARMS, alarmid);
				if (data != null && data.size() > 0) {
					JSONObject alarm = data.get(0);

					String user2check = alarm.getString("toNotify");

					if (user2check.equals(user.getName())) {
						DataService.removeGenericData(ALARMS, alarmid);
						List<JSONObject> alarms = DataService.getGenericData(ALARMS, null);
						initialAlarms = new JSONArray();
						for (JSONObject alarm2 : alarms) {
							initialAlarms.put(alarm2);
						}
						amt.updateMonitors(initialAlarms);
						return true;
					}
				}

			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Could not delete alarm", e);
				return false;
			}
		}
		return false;
	}

	private boolean deleteAlarmV2(User user, String alarmid) {
		if (user != null && alarmid != null) {
			try {
				List<JSONObject> data = DataService.getGenericData(ALARMSV2, alarmid);
				if (data != null && data.size() > 0) {
					JSONObject alarm = data.get(0);
					String ownerField = "owner";
					if(!alarm.has("owner")) {
						ownerField = "user";
					}
					String user2check = alarm.getJSONObject(ownerField).getString("objectId");
					if (user2check.equals(user.getUID())) {
						DataService.removeGenericData(ALARMSV2, alarmid);
						List<JSONObject> alarms = DataService.getGenericData(ALARMSV2, null);
						initialAlarmsV2 = new JSONArray();
						for (JSONObject alarm2 : alarms) {
							initialAlarmsV2.put(alarm2);
						}
						amt2.updateMonitors(initialAlarmsV2);
						return true;
					}
				}

			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Could not delete Alarm", e);
				return false;
			}
		}
		return false;
	}

	private String registerAlarmV2(User user, JSONObject parameter)
			throws IllegalArgumentException, IllegalAccessException {

		if (parameter == null) {
			throw new IllegalArgumentException(
					"Could not create Alarm due to missing all of the following parameters:\n trigger, action, item_source, item_id, item_dimension, owner");
		}
		if (parameter.has("owner")) {
			parameter.put("user", parameter.get("owner"));
			parameter.remove("owner");
		}
		if (!(parameter.has("trigger") && parameter.has("action") && parameter.has("item_source")
				&& parameter.has("item_id") && parameter.has("item_dimension") && parameter.has("user"))) {
			//Missing Parameters
			throw new IllegalArgumentException(
					"Could not create Alarm due to missing one of the following parameters:\n trigger, action, item_source, item_id, item_dimension, owner");
		}
		if (!(parameter.getJSONObject("trigger").has("type"))) {
			//Missing Parameters in trigger
			throw new IllegalArgumentException("Could not create Alarm due to missing type parameter in trigger");
		}
		if (!(parameter.getJSONObject("action").has("type"))) {
			//Missing Parameters in action
			throw new IllegalArgumentException("Could not create Alarm due to missing type parameter in action");
		}

		//Parameters seem OK!
		if ((user == null
				|| !(user.canAccessRead(parameter.getString("item_source"), parameter.getString("item_id"))))) {
			//Not authorized
			throw new IllegalAccessException("Not authorized to create alarm for this data source / sensor");

		}

		String id = parameter.optString("objectId");
		if (id.equals(""))
			id = parameter.optString("_id");
		if (id.equals(""))
			id = null;
		parameter.remove("objectId");
		parameter.remove("_id");
		String ownerField = "owner";
		if (!parameter.has("owner")) {
			ownerField = "user";
		}
		parameter.getJSONObject(ownerField).put("__type", "Pointer");
		parameter.getJSONObject(ownerField).put("className", "_User");
		JSONObject acl = new JSONObject();
		JSONObject userAcl = new JSONObject();
		userAcl.put("read", true);
		userAcl.put("write", true);
		acl.put(user.getUID(), userAcl);
		parameter.put("ACL", acl);
		try {
			id = DataService.storeGenericData(ALARMSV2, id, parameter);
			if (id == null)
				throw new IllegalArgumentException("Could not store alarm");
			amt2.refresh(true);
			return id;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}

		

	}

	@Override
	public void registerRoutes() {
		post(ALARM_EVENT_GET_API, (req, res) -> {
			String userID = req.params("userid");
			User user = req.session().attribute("user");
			JSONObject parameter = new JSONObject(req.body());
			boolean canRead = userID.equals(user.getName());
			if (Config.getBool("accessControl", true) && !canRead) {

				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
						"You are not allowed to add the alarm for this user");
			}

			//V1
			parameter.put("toNotify", userID);
			String success = registerAlarmV1(user, parameter);
			if (success != null) {
				JSONObject result = new JSONObject();
				result.put("message", "Succesfully created Alarm");
				result.put("id", success);
				return HTTPResponseHelper.generateResponse(res, 200, result, null);
			} else {
				JSONObject result = new JSONObject();
				result.put("message", "Could not create Alarm");
				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null,
						result);
			}

		});

		post(ALARM_EVENT_GET_APIV2, (req, res) -> {
			String userID = req.params("userid");
			User user = req.session().attribute("user");
			JSONObject parameter = new JSONObject(req.body());
			boolean canRead = userID.equals(user.getName());
			if (Config.getBool("accessControl", true) && !canRead) {
				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
						"You are not allowed to add the alarm for this user");
			}

			if (!(parameter.has("trigger") && parameter.has("action")))
				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
						"Request is missing attributes. 'action' and 'trigger' is required");

			try {
				String success = registerAlarmV2(user, parameter);
				return HTTPResponseHelper.generateResponse(res, 200, success, null);
			} catch (Exception e) {
				if (e instanceof IllegalAccessException) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							e.getMessage());
				} else {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null,
							e.getMessage());
				}

			}

		});
		OpenWareInstance.getInstance().logTrace("[ROUTE]" + "DELETE:" +
												ALARM_EVENT_DELETE_API);
		delete(ALARM_EVENT_DELETE_API, (req, res) -> {

			String userID = req.params("userid");
			String alarmid = req.params("alarmid");
			User user = req.session().attribute("user");
			if (Config.getBool("accessControl", true)) {
				if (user == null || !user.getName().equals(userID))
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not allowed to delete alarm");
			}

			if (deleteAlarm(user, alarmid)) {
				JSONObject result = new JSONObject();
				result.put("msg", "Successfully deleted alarm!");
				result.put("deleted", alarmid);
				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_OK, result,
						null);
			} else {

				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
						"You must provide a user and existing alarm id");

			}

		});
		OpenWareInstance.getInstance().logTrace("[ROUTE]" + "DELETE:" +
												ALARM_EVENT_DELETE_APIV2);
		delete(ALARM_EVENT_DELETE_APIV2, (req, res) -> {

			String userID = req.params("userid");
			String alarmid = req.params("alarmid");
			User user = req.session().attribute("user");
			if (Config.getBool("accessControl", true)) {
				if (user == null || !user.getName().equals(userID))
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not allowed to delete alarm");
			}
			
			if (deleteAlarmV2(user, alarmid)) {
				JSONObject result = new JSONObject();
				result.put("msg", "Successfully deleted alarm!");
				result.put("deleted", alarmid);
				return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_OK, result,
						null);
			}
			return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
					"You must provide a user and alarm id");

		});
		OpenWareInstance.getInstance().logTrace("[ROUTE]" + "GET:" +
												ALARM_EVENT_GET_API);
		get(ALARM_EVENT_GET_API, (req, res) -> {
			String userID = req.params("userid");
			User user = req.session().attribute("user");
			if (Config.getBool("accessControl", true)) {

				boolean canRead = userID.equals(user.getName());
				if (!canRead) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not allowed to access alarms");
				}

			}
			JSONArray userAlarms = new JSONArray();
			for (int i = 0; i < initialAlarms.length(); i++) {
				JSONObject current = initialAlarms.getJSONObject(i);
				if (current.has("toNotify") && current.getString("toNotify").equals(userID)) {
					userAlarms.put(current);
				}
			}
			res.status(200);
			return userAlarms;
		});
		OpenWareInstance.getInstance().logTrace("[ROUTE]" + "GET:" +
												ALARM_EVENT_GET_APIV2);
		get(ALARM_EVENT_GET_APIV2, (req, res) -> {
			amt2.refresh();
			String userID = req.params("userid");
			User user = req.session().attribute("user");
			/*
			if (Config.getBool("accessControl", true)) {
				boolean canRead = userID.equals(user.getName());
				if (!canRead) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not allowed to access alarms");
				}

			}
			*/
			JSONArray userAlarms = new JSONArray();
			Collection<JSONArray> registeredAlarms = amt2.getCurrentAlarms().values();

			for (JSONArray cAlarmset : registeredAlarms) {
				for (int i = 0; i < cAlarmset.length(); i++) {
					JSONObject current = cAlarmset.getJSONObject(i);
					String ownerField = "owner";
					if (!current.has("owner")) {
						ownerField = "user";
					}
					if (current.has(ownerField)
							&& current.getJSONObject(ownerField).getString("objectId").equals(user.getUID())) {
						userAlarms.put(current);
					}
				}
			}

			res.status(200);
			return userAlarms;
		});
		OpenWareInstance.getInstance().logTrace("Alarm routes have been registered");
	}

}

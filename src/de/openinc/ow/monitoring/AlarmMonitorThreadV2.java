package de.openinc.ow.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
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
import de.openinc.ow.monitoring.Condition.ConditionValueHolder;

public class AlarmMonitorThreadV2 {
	private final String ALARM_TRIGGER_STATE = "ALARM_TRIGGER_STATES";
	private HashMap<String, List<JSONObject>> alarms;
	private HashMap<String, Long> sentTS;
	private HashMap<String, Boolean> lastEventTriggered;
	private HashMap<String, OpenWareDataItem> lastSentEventItem;
	private long lastUpdate;
	private String alarmStatesPersistenceID;
	private boolean refreshing;

	public AlarmMonitorThreadV2(List<JSONObject> alarms) {
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
				if (key.equals("_id"))
					continue;
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
				// Update Alarms regularly to make sure alarms that have been added through
				// other systems will be recognized
				refresh();
				OpenWareDataItem currentItem = item;
				OpenWareDataItem lastReceivedItem = old;

				// Looking for Alarm of item that was just received
				List<JSONObject> alarm = getEventAlarms(item.getSource(), item.getId());
				if (alarm == null || alarm.size() == 0)
					return;
				// An alarm exists... Better check if update is necessary
				refresh();

				// re-check alarms to see if alarm is still there...
				alarm = getEventAlarms(item.getSource(), item.getId());
				if (alarm == null || alarm.size() == 0)
					return;

				for (JSONObject cAlarm : alarm) {
					evaluateAlarm(cAlarm, currentItem, lastReceivedItem);
				}
			}

		});
		startWatchdogs();

	}

	private List<JSONObject> getEventAlarms(String source, String id) {
		if (!alarms.containsKey(source + Config.get("idseperator", "---") + id))
			return null;
		return alarms.get(source + Config.get("idseperator", "---") + id).stream().filter(cAlarm -> {
			return !cAlarm.getJSONObject("trigger").getString("type").startsWith("ts_last");
		}).toList();
	}

	private void evaluateAlarm(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastReceivedItem)
			throws JSONException, Exception {

		Rule mainRule = Rule.fromJSON(currentObj.getJSONObject("trigger"));
		boolean lastTriggered = lastEventTriggered.getOrDefault(currentObj.getString("_id"), false);
		boolean triggered = false;

		int dim = currentObj.getInt("item_dimension");

		String ownerField = "owner";
		if (!currentObj.has("owner")) {
			ownerField = "user";
		}
		User toNotify = UserService.getInstance()
				.getUserByUID(currentObj.getJSONObject(ownerField).getString("objectId"));

		triggered = mainRule.check(currentItem, lastReceivedItem, null, dim);

		JSONObject conditions = currentObj.optJSONObject("condition");
		boolean conditionsMet = false;
		try {
			conditionsMet = conditions == null || Condition.fromJSON(conditions, toNotify).checkCondition(
					new ConditionValueHolder(currentItem, dim), new ConditionValueHolder(lastReceivedItem, dim));
		} catch (Exception e) {
			e.printStackTrace();
			OpenWareInstance.getInstance()
					.logWarn("Could not evaluate alarm conditions due to error\n" + e.getMessage());
			return;
		}

		long checkedTS = System.currentTimeMillis();

		if (triggered && conditionsMet) {
			// Alarm was triggered
			lastEventTriggered.put(currentObj.getString("_id"), true);

			boolean shouldSendAgain = (!lastTriggered
					|| (lastTriggered && checkedTS - sentTS.getOrDefault(currentObj.getString("_id"), 0l) > currentObj
							.getJSONObject("trigger").getLong("interval")));

			boolean userIsAllowed = toNotify != null
					&& toNotify.canAccessRead(currentObj.getString("item_source"), currentObj.getString("item_id"));

			if (shouldSendAgain && userIsAllowed) {
				// Notification needs to be send again

				OpenWareDataItem notifyItem = currentItem.cloneItem();

				// Get the item that was created when the alarm was triggered the last time
				OpenWareDataItem lastSentAlarmItem = lastSentEventItem.getOrDefault(currentObj.getString("_id"),
						lastReceivedItem);

				if (lastSentAlarmItem != null && lastSentAlarmItem.value().size() > 0) {
					notifyItem.value().add(lastSentAlarmItem.value().get(0));
				}

				ActuatorAdapter actor = DataService.getActuator(currentObj.getJSONObject("action").getString("type"));
				JSONObject options = currentObj.getJSONObject("action");
				JSONObject optionsItem = new JSONObject();
				optionsItem.put("source", currentObj.get("item_source"));
				optionsItem.put("id", currentObj.get("item_id"));
				optionsItem.put("dimension", currentObj.get("item_dimension"));
				optionsItem.put("unit", currentItem.getValueTypes().get(currentObj.getInt("item_dimension")).getUnit());
				optionsItem.put("datetime", new Date(currentItem.value().get(0).getDate()).toLocaleString());
				optionsItem.put("timestamp", currentItem.value().get(0).getDate());
				optionsItem.put("value", currentItem.value().get(0).get(currentObj.getInt("item_dimension")).value());
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

	public HashMap<String, List<JSONObject>> updateMonitors(List<JSONObject> alarms) {
		lastUpdate = System.currentTimeMillis();
		HashMap<String, List<JSONObject>> newAlarms = new HashMap<String, List<JSONObject>>();

		for (JSONObject current : alarms) {
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
				String tempIndex = item_source + Config.get("idseperator", "---") + item_id;
				List<JSONObject> idAlarms = newAlarms.getOrDefault(tempIndex, new ArrayList<JSONObject>());
				idAlarms.add(current);
				newAlarms.put(tempIndex, idAlarms);
			}
		}
		this.alarms = newAlarms;
		return this.alarms;
	}

	public HashMap<String, List<JSONObject>> getCurrentAlarms() {
		return this.alarms;
	}

	public void startWatchdogs() {
		OpenWareInstance.getInstance().getCommonExecuteService().scheduleAtFixedRate(() -> {
			refresh();
			alarms.values().stream().forEach(cAlarms -> {
				cAlarms.stream().filter(cAlarm -> {
					return cAlarm.getJSONObject("trigger").getString("type").startsWith("ts_last");
				}).forEach(cAlarm -> {
					OpenWareDataItem currentValue = DataService.getLiveSensorData(cAlarm.getString("item_id"),
							cAlarm.getString("item_source"));
					if (currentValue == null) {
						OpenWareInstance.getInstance()
								.logWarn("Watchdog for non-existing sensor " + cAlarm.getString("item_source") + "---"
										+ cAlarm.getString("item_id") + "(" + cAlarm.getString("_id") + ")");
						return;
					}
					try {
						evaluateAlarm(cAlarm, currentValue, currentValue);
					} catch (Exception e) {
						OpenWareInstance.getInstance().logError("Could not evaluate Watchdog-Alarm", e);
					}
				});
			});
		}, 0, 1, TimeUnit.MINUTES);
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

		List<JSONObject> alarmsCheck = null;
		try {
			OpenWareInstance.getInstance().logDebug("Refreshing alarms...");
			alarmsCheck = DataService.getGenericData(AlarmAPI.ALARMSV2, null);
			updateMonitors(alarmsCheck);
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

}

package de.openinc.ow.core.api.analytics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.AnalyticSensorProvider;
import de.openinc.ow.core.api.AnalyticsProvider;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

public class AnalyticsService {
	Map<String, AnalyticsProvider> provider;
	Map<String, Map<String, OpenWareDataItem>> items;
	AnalyticSensorProvider sensorProviders;
	private static AnalyticsService me;
	private long lastRefresh;

	private AnalyticsService() {
		this.provider = new HashMap<String, AnalyticsProvider>();
		this.items = new HashMap<>();
		me = this;
		this.lastRefresh = 0;

	}

	public OpenWareDataItem handle(String user, String sensor) throws Exception {
		Map<String, OpenWareDataItem> aItems = items.get(user);
		if (aItems == null) {
			OpenWareInstance.getInstance()
					.logError("Analytics Service trying to handle request from unknown user:" + user +
								" " +
								sensor);
			return null;
		}

		OpenWareDataItem analItem = aItems.get(sensor);
		String owner = analItem.getMeta().getString("owner");
		// analItem.value(new ArrayList<OpenWareValue>());
		List<OpenWareDataItem> data = new ArrayList<OpenWareDataItem>();

		String sensorToAccess = analItem.getMeta().getString("sensor");
		if (sensorToAccess.startsWith(Config.analyticPrefix)) {
			data.add(me.handle(owner, analItem.getMeta().getString("sensor")));
		} else {
			if (Config.accessControl) {
				if (!UserService.getInstance().getUser(user).canAccessRead(owner, sensorToAccess))
					return null;
			}
			data.add(DataService.getLiveSensorData(sensorToAccess, owner));
		}
		String op = analItem.getMeta().getString("operation");
		if (!provider.containsKey(op)) {
			return null;
		}
		return provider.get(op).process(analItem, data);
	}

	public OpenWareDataItem handle(String user, String sensor, long start, long end) throws Exception {
		Map<String, OpenWareDataItem> aItems = items.get(user);
		OpenWareDataItem analItem = aItems.get(sensor);
		// analItem.value(new ArrayList<OpenWareValue>());
		JSONArray sensors = analItem.getMeta().optJSONArray("sensor");
		List<OpenWareDataItem> data = new ArrayList<OpenWareDataItem>();
		if (sensors != null) {

			for (int i = 0; i < sensors.length(); i++) {
				String sensorToAccess = sensors.getJSONArray(i).getString(1);
				String owner = sensors.getJSONArray(i).getString(0);
				if (sensorToAccess.startsWith(Config.analyticPrefix)) {
					data.add(me.handle(user, sensorToAccess, start, end));
				} else {
					if (Config.accessControl) {
						if (!UserService.getInstance().getUser(user).canAccessRead(owner, sensorToAccess))
							return null;
					}
					OpenWareDataItem dataItem = DataService.getHistoricalSensorData(sensorToAccess, owner, start, end);
					if (dataItem != null)
						data.add(dataItem);
				}
			}

		}

		String op = analItem.getMeta().getString("operation");
		if (!provider.containsKey(op)) {
			return null;
		}
		return provider.get(op).process(analItem, data);
	}

	public AnalyticsProvider registerAnalyticsProvider(String operation, AnalyticsProvider provider) {
		return this.provider.put(operation, provider);
	}

	public AnalyticsProvider deregisterAnalyticsProvider(String operation) {
		return this.provider.remove(operation);
	}

	public boolean setSensorProvider(AnalyticSensorProvider provider) {
		if (provider == null) {
			return false;
		}
		this.sensorProviders = provider;
		refreshSensors();
		return true;
	}

	public Map<String, OpenWareDataItem> getAnalyticSensors(User user) {
		Map<String, OpenWareDataItem> current;
		if (new Date().getTime() - lastRefresh > Config.analyticRefreshRate) {
			refreshSensors();
		}
		current = items.get(user.getName());
		if (current == null) {
			return new HashMap<String, OpenWareDataItem>();
		}
		return current;
	}

	public void refreshSensors() {
		if (sensorProviders != null) {
			items.clear();
			Map<String, Map<String, OpenWareDataItem>> newItems = sensorProviders.getAnalyticSensors();
			for (String user : newItems.keySet()) {
				// Add flag to mark sensor as virtual one
				for (String sensor : newItems.get(user).keySet()) {
					newItems.get(user).get(sensor).getMeta().put("virtualSensor", true);
				}
				Map<String, OpenWareDataItem> current = items.getOrDefault(user, new HashMap());
				current.putAll(newItems.get(user));
				items.put(user, current);
			}

			this.lastRefresh = new Date().getTime();
		}

	}

	public static AnalyticsService getInstance() {
		if (me == null) {
			return new AnalyticsService();
		}
		return me;
	}

	public JSONArray getAvailableOperations() {
		JSONArray res = new JSONArray();

		for (AnalyticsProvider prov : provider.values()) {
			res.put(new JSONObject(prov.getFormTemplate()));
		}

		return res;
	}

	public boolean saveAnalyticSensor(JSONObject parameter) {
		return sensorProviders.addAnalyticSensor(parameter);
	}

	public boolean deleteSensor(String user, String sensorid) {
		return sensorProviders.deleteAnalyticSensor(user, sensorid);
	}

	public static JSONObject loadConfigfromJSONFile(String oid) {
		try {
			File file = new File("analytics/" + oid +
									".json");
			FileInputStream fis;
			fis = new FileInputStream(file);
			byte[] data = new byte[(int) file.length()];
			fis.read(data);
			fis.close();
			String str = new String(data);

			JSONObject res = new JSONObject(str);
			res.put("operationid", oid);
			return res;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}
}

package de.openinc.ow.middleware.services;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import de.openinc.api.AnalyticSensorProvider;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.helper.Config;

public class AnalyticsService {

	private HashMap<String, AnalyticSensorProvider> providers;
	private HashMap<String, OpenWareDataItem> cachedSensors = new HashMap();
	private long lastRefresh;
	private static AnalyticsService me;

	private AnalyticsService() {
		cachedSensors = new HashMap<String, OpenWareDataItem>();
		providers = new HashMap<String, AnalyticSensorProvider>();
		me = this;
	}

	public OpenWareDataItem handle(User user, String sensor, long start, long end) throws Exception {
		String[] idParts = sensor.split(".");
		if (idParts.length < 3) {
			throw new IllegalArgumentException("Malformed V-Sensor Id: " + sensor);
		}
		AnalyticSensorProvider provider = providers.get(idParts[1]);
		if (provider == null)
			throw new IllegalArgumentException("V-Sensor Provider is not registered");
		return provider.handle(user, sensor.replace(idParts[0] + "." + idParts[1] + ".", ""), start, end);
	}

	public AnalyticSensorProvider addSensorProvider(AnalyticSensorProvider provider) {
		return providers.put(provider.getType(), provider);
	}

	public Map<String, OpenWareDataItem> getAnalyticSensors() {
		if (lastRefresh + Config.getLong("virtual_sensor_refresh_interval", 5000l) < System.currentTimeMillis()) {
			lastRefresh = System.currentTimeMillis();
			cachedSensors.clear();
			for (AnalyticSensorProvider provider : providers.values()) {
				Map<String, OpenWareDataItem> cVSensors = provider.getAnalyticSensors();
				for (String key : cVSensors.keySet()) {
					OpenWareDataItem item = cVSensors.get(key);
					item.setId(Config.get("analyticPrefix", "analytic.") + provider.getType() + "." + item.getId());
					cachedSensors.put(item.getId(), item);
				}

			}

		}
		return cachedSensors;
	}

	public static AnalyticsService getInstance() {
		if (me == null) {
			return new AnalyticsService();
		}
		return me;
	}

	public String saveAnalyticSensor(String type, JSONObject parameter, JSONObject acl) throws Exception {
		AnalyticSensorProvider provider = providers.get(type);
		if (provider == null)
			throw new IllegalArgumentException("V-Sensor Provider is not registered");
		return provider.addAnalyticSensor(parameter, acl);
	}

	public boolean deleteSensor(String type, User user, String sensorid) {
		AnalyticSensorProvider provider = providers.get(type);
		if (provider == null)
			return false;
		return provider.deleteAnalyticSensor(user, sensorid);
	}

}

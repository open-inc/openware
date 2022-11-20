package de.openinc.ow.middleware.services;

import java.util.Map;

import org.json.JSONObject;

import de.openinc.api.AnalyticSensorProvider;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;

public class AnalyticsService {

	AnalyticSensorProvider provider;
	private static AnalyticsService me;

	private AnalyticsService() {

		me = this;
	}

	public OpenWareDataItem handle(User user, String sensor, long start, long end) throws Exception {
		OpenWareDataItem vItem = provider.getAnalyticSensors(user).get(sensor);
		JSONObject pipe = vItem.getMeta().getJSONObject("pipe");
		JSONObject templateStage = new JSONObject();
		templateStage.put("action", "template");
		templateStage.put("params", new JSONObject());
		templateStage.getJSONObject("params").put("template", vItem.cloneItem(false).toJSON());
		pipe.getJSONArray("stages").put(templateStage);
		pipe.put("start", start);
		pipe.put("end", end);
		return TransformationService.getInstance().pipeOperations(user, null, pipe);
	}

	public boolean setSensorProvider(AnalyticSensorProvider provider) {
		if (provider == null) {
			return false;
		}
		this.provider = provider;
		return true;
	}

	public Map<String, OpenWareDataItem> getAnalyticSensors(User user) {
		return this.provider.getAnalyticSensors(user);
	}

	public static AnalyticsService getInstance() {
		if (me == null) {
			return new AnalyticsService();
		}
		return me;
	}

	public String saveAnalyticSensor(JSONObject parameter, JSONObject acl) {
		return provider.addAnalyticSensor(parameter, acl);
	}

	public boolean deleteSensor(User user, String sensorid) {
		return provider.deleteAnalyticSensor(user, sensorid);
	}

}

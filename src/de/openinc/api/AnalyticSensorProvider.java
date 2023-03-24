package de.openinc.api;

import java.util.Map;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;

public interface AnalyticSensorProvider {

	public Map<String, OpenWareDataItem> getAnalyticSensors();

	public boolean deleteAnalyticSensor(User user, String sensorid);

	public String addAnalyticSensor(JSONObject parameter, JSONObject acl);

	public String getType();

	public OpenWareDataItem handle(User user, String sensor, long start, long end) throws Exception;

	public void init(JSONObject options) throws Exception;

}

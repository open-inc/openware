package de.openinc.api;

import java.util.Map;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;

public interface AnalyticSensorProvider {

	public Map<String, OpenWareDataItem> getAnalyticSensors(User user);

	public boolean deleteAnalyticSensor(User user, String sensorid);

	public String addAnalyticSensor(JSONObject parameter, JSONObject acl);

}

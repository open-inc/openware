package de.openinc.api;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;

public interface AnalyticSensorProvider {

	public Map<String, Map<String, OpenWareDataItem>>getAnalyticSensors();
	public boolean deleteAnalyticSensor(String user, String sensorid);
	public boolean addAnalyticSensor(JSONObject parameter);
	
}

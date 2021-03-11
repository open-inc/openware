package de.openinc.api;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface PersistenceAdapter {

	void init();

	void close();

	JSONArray getStats();

	List<OpenWareDataItem> getItems();

	OpenWareDataItem getLiveSensorData(String sensorName, String user);

	OpenWareDataItem getHistoricalSensorData(String sensorName, String user, Long timestamp, Long until);

	boolean deleteDeviceData(String sensorName, String user, Long from, Long until);

	void storeData(OpenWareDataItem item) throws Exception;

	String storeGenericData(String type, String optionalKey, JSONObject value) throws Exception;

	void removeGenericData(String user, String key) throws Exception;

	List<JSONObject> getGenericData(String type, String key) throws Exception;

}
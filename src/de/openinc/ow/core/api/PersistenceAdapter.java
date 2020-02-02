package de.openinc.ow.core.api;

import java.util.List;

import org.json.JSONArray;

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

	void storeGenericData(String type, String key, String value);

	void removeGenericData(String user, String key);

	String[] getGenericData(String type, String key);

}
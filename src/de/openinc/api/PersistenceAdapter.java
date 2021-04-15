package de.openinc.api;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;

public interface PersistenceAdapter {

	void init();

	void close();

	JSONArray getStats();

	List<OpenWareDataItem> getItems();

	OpenWareDataItem liveData(String sensorName, String source, long at, int lastElements, String reference);

	OpenWareDataItem historicalData(String sensorName, String source, Long timestamp, Long until, String reference);

	boolean deleteDeviceData(String sensorName, String source, Long from, Long until);

	void storeData(OpenWareDataItem item) throws Exception;
	
	int updateData(OpenWareDataItem item) throws Exception;

	String storeGenericData(String type, String optionalKey, JSONObject value) throws Exception;

	void removeGenericData(String user, String key) throws Exception;
	

	List<JSONObject> getGenericData(String type, String key) throws Exception;

}
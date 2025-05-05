package de.openinc.api;

import java.util.List;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;

public abstract class DataHandler {

	public abstract List<OpenWareDataItem> handleData(String id, String data) throws Exception;

	public abstract boolean setOptions(JSONObject options) throws Exception;

	public boolean acceptData(String topic) {
		return true;
	}
}

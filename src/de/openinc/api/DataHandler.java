package de.openinc.api;

import java.util.List;

import org.json.JSONObject;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface DataHandler {
	public List<OpenWareDataItem> handleData(String id, String data) throws Exception;

	public boolean setOptions(JSONObject options) throws Exception;
}

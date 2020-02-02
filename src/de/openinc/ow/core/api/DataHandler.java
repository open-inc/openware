package de.openinc.ow.core.api;

import java.util.List;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface DataHandler {

	public List<OpenWareDataItem> handleData(String id, String data) throws Exception;
}

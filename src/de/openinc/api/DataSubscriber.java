package de.openinc.api;

import de.openinc.model.data.OpenWareDataItem;

public interface DataSubscriber {

	public void receive(OpenWareDataItem oldItem, OpenWareDataItem newItem) throws Exception;

}

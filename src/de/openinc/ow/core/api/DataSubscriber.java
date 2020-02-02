package de.openinc.ow.core.api;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface DataSubscriber {

	public void receive(OpenWareDataItem item) throws Exception;

}

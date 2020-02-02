package de.openinc.ow.core.api;

import java.util.List;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface AnalyticsProvider {

	public OpenWareDataItem process(OpenWareDataItem base, List<OpenWareDataItem> data) throws Exception;

	public String getFormTemplate();

}

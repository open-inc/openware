package de.openinc.ow.middleware.transformation;

import org.json.JSONObject;

import de.openinc.ow.core.api.transformation.TransformationOperation;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.middleware.services.DataService;

public class RawDataSource implements TransformationOperation {
	private final static String sourceID = "source.ow.raw";
	private OpenWareDataItem result;

	@Override
	public OpenWareDataItem getResult() {

		return result;
	}

	@Override
	public TransformationOperation apply(OpenWareDataItem old, JSONObject params) throws Exception {

		if (!(params.has("id") && params.has("source") && params.has("start") && params.has("end"))) {
			throw new IllegalArgumentException("Missing one of the following parameters: id, source, start, end");
		}
		OpenWareDataItem res = DataService.getHistoricalSensorData(params.getString("id"), params.getString("source"),
				params.getLong("start"), params.getLong("end"));
		if (old != null) {
			res.value().addAll(old.value());
		}
		result = res;
		return this;
	}

}

package de.openinc.ow.middleware.transformation;

import org.json.JSONObject;

import de.openinc.ow.analytics.geoanalytics.GeoHelper;
import de.openinc.ow.core.api.transformation.TransformationOperation;
import de.openinc.ow.core.model.data.OpenWareDataItem;

public class GeoTransformer implements TransformationOperation {
	OpenWareDataItem res;

	@Override
	public TransformationOperation apply(OpenWareDataItem old, JSONObject params) throws Exception {
		if (old == null)
			throw new IllegalArgumentException("GeoTranformer requires previous stage to provide data");
		if (params.getString("operation").toLowerCase().equals("extractcoord")) {
			res = GeoHelper.fromGeoJSON(old);
		}

		if (params.getString("operation").toLowerCase().equals("clusterkmeans")) {
			res = GeoHelper.clusterKMeans(old, params);
		}
		if (params.getString("operation").toLowerCase().equals("clusterdbscan")) {
			res = GeoHelper.clusterDensity(old, params);
		}

		return this;
	}

	@Override
	public OpenWareDataItem getResult() {
		// TODO Auto-generated method stub
		return res;
	}

}

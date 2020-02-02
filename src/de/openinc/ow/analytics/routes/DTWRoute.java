package de.openinc.ow.analytics.routes;

import java.util.Map;

import org.json.JSONObject;

import de.openinc.ow.analytics.model.Dataset;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Created by Martin on 18.10.2016.
 */
public class DTWRoute implements Route {

	private static final String DAILY = "daily";
	private static final String WEEKLY = "weekly";
	public static final String HOURLY = "hourly";
	private Map<String, Dataset> datasets;

	public DTWRoute(Map<String, Dataset> current) {
		datasets = current;
	}

	public Object handle(Request request, Response response) throws Exception {
		JSONObject resp = new JSONObject();
		JSONObject options = new JSONObject();
		long epoch = -1;
		int kcluster = 0;
		if (request.params("epoch").toLowerCase().equals(DAILY)) {
			epoch = 1000l * 24l * 60l * 60l;
			kcluster = 7;
		}
		if (request.params("epoch").toLowerCase().equals(WEEKLY)) {
			epoch = 1000l * 24l * 60l * 60l * 7l;
			kcluster = 7;
		}
		if (request.params("epoch").toLowerCase().equals(HOURLY)) {
			epoch = 1000l * 60l * 60l;
			kcluster = 7;
		}
		if (epoch == -1) {
			resp.put("status", "error");
			resp.put("cause",
					"Epoch " + request.params("epoch") + " not allowed. Use 'weekly', 'daily','hourly' instead.");
			return resp.toString();
		}

		Dataset before = datasets.get(request.params("name"));
		if (before == null || before.size() == 0) {
			resp.put("status", "error");
			resp.put("cause", "No Data found for " + request.params("name"));
			return resp.toString();
		}
		//TODO: Parameter aus URL bzw. Options
		int index = 0;
		//Dataset x = DataSplitter.convertToEpochInstances(before,epoch,index);
		//DensityBasedSpatialClustering dbscan = new DensityBasedSpatialClustering();

		return null;
	}
}

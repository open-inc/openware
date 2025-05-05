package de.openinc.ow.analytics.routes;

import java.util.Map;

import de.openinc.ow.analytics.model.Dataset;

/**
 * Created by Martin on 18.10.2016.
 */
public class ClusterRoute {

	/**
	 * amount of neighbors: cluster core criteria Paramenter can be set for density
	 * Based clustering
	 */
	public static final String MIN_PTS = "minPTS";
	/**
	 * Distance to neighbors: cluster core criteria Paramenter can be set for
	 * density Based clustering
	 */
	public static final String EPSILON = "epsilon";
	/**
	 * Weight of time in euclidean Distance compared to energy level Paramenter can
	 * be set for density Based clustering
	 */
	public static final String TIMEWEIGHT = "timeweight";
	public static final String TIMEMODE = "timemode";
	public static final String REL_MIN_SIZE = "relMinSize";
	public static final String MERGETHRESHOLD = "mergethreshold";
	private Map<String, Dataset> datasets;

	public ClusterRoute(Map<String, Dataset> current) {
		datasets = current;
	}
	/*-
		@Override
		public Object handle(Request request, Response response) throws Exception {
			JSONObject res = new JSONObject();
			JSONObject resp = new JSONObject();
			Dataset before = datasets.get(request.params("name"));
			JSONObject options = null;
			try {
				options = new JSONObject(request.body());
			} catch (JSONException e) {
				resp.put("status", "error");
				resp.put("cause", "Couldn't parse Options " + request.body());
				return resp.toString();
			}
	
			if (before == null || before.size() == 0) {
				resp.put("status", "error");
				resp.put("cause", "No Data found for " + request.params("name"));
				return resp.toString();
			}
			options = prepareOptions(options);
			resp.put("status", "OK");
			resp.put("data", res);
			resp.put("options", options);
			return resp.toString();
		}
	
		private JSONObject clusterDB(Dataset data, JSONObject options) {
			return null;
		}
	
		private JSONObject prepareOptions(JSONObject options) {
			double mergeFactor;
			try {
				mergeFactor = options.getDouble(MERGETHRESHOLD);
			} catch (Exception e) {
				mergeFactor = 0.05;
				options.put(MERGETHRESHOLD, mergeFactor);
				System.out.println("Default merge threshold " + mergeFactor);
			}
			double timeweight;
			try {
				timeweight = options.getDouble(TIMEWEIGHT);
			} catch (Exception e) {
				timeweight = 0;
				options.put(TIMEWEIGHT, 0.0);
				System.out.println("Default timeweight " + timeweight);
			}
			String mode;
			try {
				mode = options.getString(TIMEMODE);
			} catch (Exception e) {
				mode = "daily";
				options.put(TIMEMODE, mode);
				System.out.println("Default timemode is set daily (daily epochs used)");
			}
			double sizeThershold;
			try {
				sizeThershold = options.getDouble(REL_MIN_SIZE);
			} catch (Exception e) {
				sizeThershold = 0.01;
				options.put(REL_MIN_SIZE, sizeThershold);
				System.out.println("Default relative minimun size set to " + sizeThershold
						+ " (clusters smaller than threshold will be cut out)");
			}
			return options;
		}
	*/
}

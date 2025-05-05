package de.openinc.ow.analytics.routes;

/**
 * Created by Martin on 18.10.2016.
 */
public class DataProcessRoute {
	/*-
		private static final String ACTION_SMOOTH = "smooth";
		private static final String ACTION_OUTLIER_REMOVAL = "removeoutlier";
		public static final String K_NEAREST_HISTOGRAM = "knearest";
		private Map<String, Dataset> datasets;
	
		public DataProcessRoute(Map<String, Dataset> current) {
			datasets = current;
		}
	
		public Object handle(Request request, Response response) throws Exception {
			JSONObject resp = new JSONObject();
			if (request.params("action").toLowerCase().equals(ACTION_SMOOTH)) {
				double window = -1;
				JSONObject options;
				try {
					options = new JSONObject(request.body());
					window = Integer.valueOf(options.optString("window"));
				} catch (NumberFormatException e) {
					resp.put("status", "error");
					resp.put("cause", "Window Paramenter needs to be provided\n" + e.getCause());
					return resp.toString();
				} catch (JSONException je) {
					resp.put("status", "error");
					resp.put("cause", "Options could not be parsed \n" + je.getCause());
					return resp.toString();
				}
				Dataset before = datasets.get(request.params("name"));
				if (before == null || before.size() == 0) {
					resp.put("status", "error");
					resp.put("cause", "No Data found for " + request.params("name"));
					return resp.toString();
				}
				Dataset x = Descriptives.smoothDataMovingAverage(before, (int) window, 0);
				datasets.put(request.params("name"), x);
				resp.put("status", "OK");
				resp.put("action", ACTION_SMOOTH);
				resp.put("options", options);
	
			}
			if (request.params("action").toLowerCase().equals(ACTION_OUTLIER_REMOVAL)) {
				double sdweight = -1;
				JSONObject options;
				try {
					options = new JSONObject(request.body());
					sdweight = Double.valueOf(options.optString("factor"));
				} catch (NumberFormatException e) {
					resp.put("status", "error");
					resp.put("cause", "Weight Paramenter needs to be provided as Float \n" + e.getCause());
					return resp.toString();
				} catch (JSONException je) {
					resp.put("status", "error");
					resp.put("cause", "Options could not be parsed \n" + je.getCause());
					return resp.toString();
				}
				Dataset before = datasets.get(request.params("name"));
				if (before == null || before.size() == 0) {
					resp.put("status", "error");
					resp.put("cause", "No Data found for " + request.params("name"));
					return resp.toString();
				}
				Dataset x = Descriptives.removeOutlier(before, 0, sdweight);
				datasets.put(request.params("name"), x);
				resp.put("status", "OK");
				resp.put("action", ACTION_OUTLIER_REMOVAL);
				resp.put("options", options);
	
			}
			if (request.params("action").toLowerCase().equals(K_NEAREST_HISTOGRAM)) {
	
				System.out.println(request.body());
				JSONObject options = new JSONObject(request.body());
				try {
					Dataset before = datasets.get(request.params("name"));
					Dataset histogram = Descriptives.movingKNearest1D(before, options.getDouble("window"), 0);
					// TODO
					resp.put("data", histogram.dataset2JSON());
				} catch (Exception e) {
					resp.put("status", "error");
					resp.put("cause", "Error " + e.getMessage());
					return resp.toString();
				}
			}
	
			return resp.toString();
		}
		*/
}

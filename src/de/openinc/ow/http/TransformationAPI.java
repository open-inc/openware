package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.helper.HTTPResponseHelper;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.AggregationService;
import de.openinc.ow.middleware.services.DataService;

public class TransformationAPI implements OpenWareAPI {

	private HashMap<String, Function<OpenWareDataItem, JSONObject, OpenWareDataItem>> pipeOperators;

	@Override
	public void registerRoutes() {
		this.pipeOperators = new HashMap<String, Function<OpenWareDataItem, JSONObject, OpenWareDataItem>>();
		this.pipeOperators.put("aggregate", (data, params) -> {
			try {
				return AggregationService.getInstance().createStatistics(data, params);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
		this.pipeOperators.put("smooth", (data, params) -> {
			try {
				return AggregationService.getInstance().movingAverage(data, params);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});

		path("/transform", () -> {
			path("/aggregate", () -> {
				get("/:source/:sensor/:start/:end", (req, res) -> {
					//:dim/:operation/:interval
					String source = req.params("source");
					String sensor = req.params("sensor");
					if (Config.accessControl) {
						User user = req.session().attribute("user");
						if (user == null || !user.canAccessRead(source, sensor))
							return HTTPResponseHelper.generateResponse(res, 403, null, "Not allowed to add data");
					}
					String oid = req.params("operation");
					long start;
					long end;

					try {
						start = Long.valueOf(req.params("start"));
						end = Long.valueOf(req.params("end"));
					} catch (NumberFormatException e) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse start/end time parameters!\n(" +	e.getMessage() +
																					")");
					}

					OpenWareDataItem data = DataService.getHistoricalSensorData(sensor, source, start, end);

					String operation = req.queryParams("operation");
					long interval;
					int dim;
					try {
						if (operation == null || operation.equals(""))
							throw new NumberFormatException("Operation parameter must not be empty!");
						interval = Long.valueOf(req.queryParams("interval"));
						dim = Integer.valueOf(req.queryParams("dimension"));
						if (data.getValueTypes().size() < (dim + 1)) {
							throw new NumberFormatException("Data source has no Dimension " + dim);
						}
					} catch (NumberFormatException e) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse query parameters!\n(" +			e.getMessage() +
																					")");
					}

					OpenWareDataItem res_data = AggregationService.getInstance().createStatistics(data, dim, operation,
							interval);
					return HTTPResponseHelper.generateResponse(res, 200, res_data.toJSON(), null);
				});
			});
			path("/smooth", () -> {
				get("/:source/:sensor/:start/:end", (req, res) -> {
					//:dim/:operation/:interval
					String source = req.params("source");
					String sensor = req.params("sensor");
					if (Config.accessControl) {
						User user = req.session().attribute("user");
						if (user == null || !user.canAccessRead(source, sensor))
							return HTTPResponseHelper.generateResponse(res, 403, null, "Not allowed to add data");
					}
					long start;
					long end;

					try {
						start = Long.valueOf(req.params("start"));
						end = Long.valueOf(req.params("end"));
					} catch (NumberFormatException e) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse start/end time parameters! (" +	e.getMessage() +
																					")");
					}

					OpenWareDataItem data = DataService.getHistoricalSensorData(sensor, source, start, end);

					int windowSize;
					int dim;
					try {
						windowSize = Integer.valueOf(req.queryParams("window"));
						dim = Integer.valueOf(req.queryParams("dimension"));
						if (data.getValueTypes().size() < (dim + 1)) {
							throw new NumberFormatException("Data source has no Dimension " + dim);
						}
					} catch (NumberFormatException e) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse query parameters!\n(" +			e.getMessage() +
																					")");
					}

					OpenWareDataItem res_data = AggregationService.getInstance().movingAverage(data, dim, windowSize);
					return HTTPResponseHelper.generateResponse(res, 200, res_data.toJSON(), null);
				});
			});
			path("/pipe", () -> {
				post("/:source/:sensor/:start/:end", (req, res) -> {
					//:dim/:operation/:interval
					String source = req.params("source");
					String sensor = req.params("sensor");
					if (Config.accessControl) {
						User user = req.session().attribute("user");
						if (user == null || !user.canAccessRead(source, sensor))
							return HTTPResponseHelper.generateResponse(res, 403, null, "Not allowed to add data");
					}
					long start;
					long end;

					try {
						start = Long.valueOf(req.params("start"));
						end = Long.valueOf(req.params("end"));
					} catch (NumberFormatException e) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse start/end time parameters! (" +	e.getMessage() +
																					")");
					}

					OpenWareDataItem data = DataService.getHistoricalSensorData(sensor, source, start, end);
					JSONObject body = new JSONObject(req.body());
					JSONArray stages = body.optJSONArray("stages");

					if (stages == null) {
						return HTTPResponseHelper.generateResponse(res, 422, null,
								"Could not parse stages!");
					}
					OpenWareDataItem tempItem = data;
					for (int i = 0; i < stages.length(); i++) {
						tempItem = pipeOperators.get(stages.getJSONObject(i).getString("action")).apply(tempItem,
								stages.getJSONObject(i).getJSONObject("params"));
						if (tempItem == null) {
							HTTPResponseHelper.generateResponse(res, 403, null,
									"Error in Stage!" + stages.getJSONObject(i));
						}
					}
					return HTTPResponseHelper.generateResponse(res, 200, tempItem.toJSON(), null);
				});
			});

		});

	}

}

interface Function<Data, Params, Result> {
	public Result apply(OpenWareDataItem Data, JSONObject Params);
}

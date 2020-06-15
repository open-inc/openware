package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.api.TransformationOperation;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.helper.HTTPResponseHelper;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.TransformationService;

public class TransformationAPI implements OpenWareAPI {

	@Override
	public void registerRoutes() {
		path("/transform", () -> {

			get("/:transformOp/:source/:sensor/:start/:end", (req, res) -> {
				JSONObject params = new JSONObject();
				for (String key : req.queryParams()) {
					params.put(key, req.queryParams(key));
				}
				TransformationOperation op = TransformationService.getInstance()
						.getOperation(req.params("transformOp"));
				if (op == null) {
					return HTTPResponseHelper.generateResponse(res, 403, null,
							"Unkown operation " + req.params("transformOp"));
				}
				String source = req.params("source");
				String sensor = req.params("sensor");
				params.put("source", source);
				params.put("id", sensor);

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
							"Could not parse start/end time parameters!\n(" +	e.getMessage() +
																				")");
				}

				params.put("start", start);
				params.put("end", end);
				int dim;
				OpenWareDataItem res_data;
				res_data = op.process(req.session().attribute("user"), null, params);

				if (Config.accessControl && res_data != null) {
					User user = req.session().attribute("user");
					if (user == null || !user.canAccessRead(res_data.getUser(), res_data.getId()))
						return HTTPResponseHelper.generateResponse(res, 403, null, "Not allowed to add data");
				}
				op = null;
				return HTTPResponseHelper.generateResponse(res, 200, res_data.toJSON(), null);
			});
			post("/pipe", (req, res) -> {
				JSONObject body = new JSONObject(req.body());
				JSONArray stages = body.optJSONArray("stages");
				User user = req.session().attribute("user");
				if (stages == null) {
					return HTTPResponseHelper.generateResponse(res, 422, null,
							"Could not parse stages!");
				}
				try {
					return HTTPResponseHelper.generateResponse(res, 200,
							TransformationService.getInstance().pipeOperations(user, null, body).toJSON(), null);

				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, 403, null, e.getMessage());
				}

			});
		});

	};

}

interface Function<Data, Params, Result> {
	public Result apply(OpenWareDataItem Data, JSONObject Params);
}

package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.api.TransformationOperation;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.TransformationService;
import io.javalin.validation.ValidationException;

public class TransformationAPI implements OpenWareAPI {

	@Override
	public void registerRoutes() {
		path("/transform", () -> {

			get("/{transformOp}/{source}/{sensor}/{start}/{end}", ctx -> {
				JSONObject params = new JSONObject();
				for (String key : ctx.queryParamMap().keySet()) {
					params.put(key, ctx.queryParam(key));
				}
				TransformationOperation op = TransformationService.getInstance()
						.getOperation(ctx.pathParam("transformOp"));
				if (op == null) {
					HTTPResponseHelper.badRequest("Unkown operation " + ctx.pathParam("transformOp"));
				}
				String source = ctx.pathParam("source");
				String sensor = ctx.pathParam("sensor");
				params.put("source", source);
				params.put("id", sensor);

				if (Config.getBool("accessControl", true)) {
					User user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessRead(source, sensor))
						HTTPResponseHelper.forbidden("Not allowed to add data");
				}

				Long start = null;
				Long end = null;
				try {
					start = ctx.pathParamAsClass("start", Long.class).get();
					end = ctx.pathParamAsClass("end", Long.class).get();
				} catch (ValidationException e) {
					HTTPResponseHelper
							.badRequest("Could not parse start/end time parameters!\n(" + e.getMessage() + ")");
				}

				params.put("start", start);
				params.put("end", end);

				OpenWareDataItem res_data;
				res_data = op.process(ctx.sessionAttribute("user"), null, params);

				if (Config.getBool("accessControl", true) && res_data != null) {
					User user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessRead(res_data.getSource(), res_data.getId()))
						HTTPResponseHelper.forbidden(
								"Not allowed to read data " + res_data.getSource() + "---" + res_data.getId());
				}
				op = null;
				HTTPResponseHelper.ok(ctx, res_data);
			});
			post("/pipe", ctx -> {
				JSONObject body = new JSONObject(ctx.body());
				JSONArray stages = body.optJSONArray("stages");
				User user = ctx.sessionAttribute("user");
				if (stages == null) {
					HTTPResponseHelper.badRequest("Could not parse stages!");
				}
				try {
					HTTPResponseHelper.ok(ctx, TransformationService.getInstance().pipeOperations(user, null, body));

				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("Could not process transform pipeline", e);
					HTTPResponseHelper.internalError("Could not process transform pipeline: " + e.getMessage());
				}

			});
		});

	};

}

interface Function<Data, Params, Result> {
	public Result apply(OpenWareDataItem Data, JSONObject Params);
}

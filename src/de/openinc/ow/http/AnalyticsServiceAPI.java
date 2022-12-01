package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.AnalyticsService;
import io.javalin.http.Context;

public class AnalyticsServiceAPI implements OpenWareAPI {
	/**
	 * API constant to get analytics data from the analytic services
	 */
	public static final String ANALYTICS = "/analytics";
	public static final String ANALYTICS_DELETE = "/analytics/{id}";

	public void handle(Context ctx) throws Exception {
		if (ctx.method().toString().toUpperCase().equals("GET")) {
			handleGet(ctx);
		}
		if (ctx.method().toString().toUpperCase().equals("POST")) {
			handlePost(ctx);
		}
		HTTPResponseHelper.badRequest("Method " + ctx.method().toString() + " not supported by Analytics Endpoint");
	}

	private void handlePost(Context ctx) {
		JSONObject parameter = new JSONObject(ctx.body());
		JSONObject vSensorObject = parameter.getJSONObject("parameters");
		JSONObject acl = parameter.getJSONObject("acl");
		AnalyticsService.getInstance().saveAnalyticSensor(vSensorObject, acl);
		HTTPResponseHelper.ok(ctx, vSensorObject);
	}

	private void handleGet(Context ctx) {
		User user = ctx.sessionAttribute("user");
		JSONObject result = new JSONObject();
		result.put("results", AnalyticsService.getInstance().getAnalyticSensors(user).values());
		ctx.json(result);

	}

	private void handleDelete(Context ctx) {
		User user = ctx.sessionAttribute("user");
		String id = ctx.pathParam("id");
		boolean success = AnalyticsService.getInstance().deleteSensor(user, id);

		try {
			if (success) {
				HTTPResponseHelper.ok(ctx, "Successfully deleted " + id);
			} else {
				HTTPResponseHelper.badRequest("Could not delete " + id);
			}

		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError("Delete Sensor Error: Could not delete sensor\n" + id, e);
			HTTPResponseHelper.badRequest("Could not delete " + id);
		}

	}

	@Override
	public void registerRoutes() {
		get(ANALYTICS, ctx -> {
			handle(ctx);
		});
		post(ANALYTICS, ctx -> {
			handle(ctx);
		});
		delete(ANALYTICS_DELETE, ctx -> {
			handleDelete(ctx);
		});

	}
}

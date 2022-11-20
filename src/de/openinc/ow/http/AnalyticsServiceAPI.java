package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.AnalyticsService;
import spark.Request;
import spark.Response;

public class AnalyticsServiceAPI implements OpenWareAPI {
	/**
	 * API constant to get analytics data from the analytic services
	 */
	public static final String ANALYTICS = "/analytics";
	public static final String ANALYTICS_DELETE = "/analytics/:id";

	public Object handle(Request request, Response response) throws Exception {
		if (request.requestMethod().equals("GET")) {
			return handleGet(request, response);
		}
		if (request.requestMethod().equals("POST")) {
			return handlePost(request, response);
		}
		return null;
	}

	private Object handlePost(Request request, Response response) {
		JSONObject parameter = new JSONObject(request.body());
		JSONObject vSensorObject = parameter.getJSONObject("parameters");
		JSONObject acl = parameter.getJSONObject("acl");
		AnalyticsService.getInstance().saveAnalyticSensor(vSensorObject, acl);
		return HTTPResponseHelper.generateResponse(response, HTTPResponseHelper.STATUS_OK, vSensorObject, null);
	}

	private Object handleGet(Request request, Response response) {
		User user = request.session().attribute("user");
		JSONObject result = new JSONObject();
		result.put("results", AnalyticsService.getInstance().getAnalyticSensors(user).values());
		return result;
	}

	private Object handleDelete(Request request, Response response) {
		User user = request.session().attribute("user");
		String id = request.params("id");
		boolean success = AnalyticsService.getInstance().deleteSensor(user, id);

		try {
			if (success) {
				return HTTPResponseHelper.generateResponse(response, HTTPResponseHelper.STATUS_OK,
						"Successfully deleted " + id, null);
			} else {
				return HTTPResponseHelper.generateResponse(response, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
						"Could not delete " + id);
			}

		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError("Delete Sensor Error: Could not delete sensor\n" + id, e);
			return HTTPResponseHelper.generateResponse(response, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
					"Could not delete " + id);
		}

	}

	@Override
	public void registerRoutes() {
		get(ANALYTICS, (req, res) -> {
			return handle(req, res);
		});
		post(ANALYTICS, (req, res) -> {
			return handle(req, res);
		});
		delete(ANALYTICS_DELETE, (req, res) -> {
			return handleDelete(req, res);
		});

	}
}

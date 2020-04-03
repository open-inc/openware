package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.middleware.services.AnalyticsService;
import spark.Request;
import spark.Response;

public class AnalyticsServiceAPI implements OpenWareAPI {
	/**
	 * API constant to get analytics data from the analytic services
	 */
	public static final String ANALYTICS = "/analytics";

	public Object handle(Request request, Response response) throws Exception {
		if (request.requestMethod().equals("GET")) {
			return handleGet(request, response);
		}
		if (request.requestMethod().equals("POST")) {
			return handlePost(request, response);
		}
		if (request.requestMethod().equals("DELETE")) {
			return handleDelete(request, response);
		}
		return null;
	}

	private Object handlePost(Request request, Response response) {
		JSONObject parameter = new JSONObject(request.body());
		AnalyticsService.getInstance().saveAnalyticSensor(parameter);
		AnalyticsService.getInstance().refreshSensors();
		return 200;
	}

	private Object handleGet(Request request, Response response) {
		JSONObject result = new JSONObject();
		result.put("results", AnalyticsService.getInstance().getAvailableOperations());
		return result;
	}

	private Object handleDelete(Request request, Response response) {
		JSONObject parameter = new JSONObject(request.body());
		try {
			AnalyticsService.getInstance().deleteSensor(parameter.getJSONObject("sensor").getString("owner"),
					parameter.getJSONObject("sensor").getString("id"));
		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError(
					"Delete Sensor Error: Could not delete sensor\n" + parameter.toString(2), e);
			return 400;
		}

		AnalyticsService.getInstance().refreshSensors();
		return 200;
	}

	@Override
	public void registerRoutes() {
		get(ANALYTICS, (req, res) -> {
			return handle(req, res);
		});
		post(ANALYTICS, (req, res) -> {
			return handle(req, res);
		});
		delete(ANALYTICS, (req, res) -> {
			return handle(req, res);
		});

	}
}

package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.path;
import static spark.Spark.post;

import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;

public class AdminAPI implements OpenWareAPI {
	/**
	 * API constants to use admin APIs
	 */
	public static final String GET_STATS = "/state";
	public static final String GET_ANALYTIC_STATS = "/getAnalyticsStats";

	/**
	 * API constant to GET/POST Sensor Config
	 */
	public static final String SENSOR_CONFIG = "/sensors";

	@Override
	public void registerRoutes() {

		path("/admin", () -> {
			post(SENSOR_CONFIG, (req, res) -> {

				User user = null;
				if (Config.accessControl) {
					user = req.session().attribute("user");
					if (user == null)
						halt(403, "You need to log in to configure items");
				}
				try {
					if (DataService.storeItemConfiguration(user, req.body())) {
						res.status(200);
						return "Successfully stored Configuration";
					} else {
						res.status(300);
						return "Could not store Configuration";
					}

				} catch (org.json.JSONException e) {
					OpenWareInstance.getInstance().logError("Malformed data posted to Sensor Config API\n" + req.body(),
							e);
					res.status(400);
					return "Malformed data posted to Sensor Config API\n" + req.body();
				} catch (SecurityException e2) {
					res.status(403);
					return "No Permission \n" + e2.getMessage() +
							"\n" +
							req.body();
				}
			});

			get(SENSOR_CONFIG, (req, res) -> {
				User user = null;
				if (Config.accessControl) {
					user = req.session().attribute("user");
					if (user == null)
						return HTTPResponseHelper.generateResponse(res, 403, null,
								"You need to log in to configure items");
				}
				try {

					return DataService.getItemConfiguration(user).values();
				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, 400, null, e.getMessage());
				}

			});
			delete(SENSOR_CONFIG + "/:owner/:sensor", (req, res) -> {
				User user = null;
				if (Config.accessControl) {
					user = req.session().attribute("user");
					if (user == null)
						halt(403, "You need to log in to configure items");
				}
				try {
					if (DataService.deleteItemConfig(user, req.params("owner"), req.params("sensor"))) {
						res.status(200);
						return "Successfully deleted Configuration";
					} else {
						res.status(300);
						return "Could not delete configuration or no configuration was assigened";
					}

				} catch (org.json.JSONException e) {
					OpenWareInstance.getInstance().logError("Malformed data posted to Sensor Config API\n" + req.body(),
							e);
					res.status(400);
					return "Malformed data posted to Sensor Config API\n" + req.body();
				} catch (SecurityException e2) {
					res.status(403);
					return "No Permission \n" + e2.getMessage() +
							"\n" +
							req.body();
				}
			});

			get(GET_STATS, (req, res) -> {
				return HTTPResponseHelper.generateResponse(res, 200, OpenWareInstance.getInstance().getState(), null);

			});

			get(GET_ANALYTIC_STATS, (req, res) -> {
				JSONObject obj = new JSONObject();
				for (String key : Config.idMappings.keySet()) {

					obj.put(key, Config.idMappings.get(key));

				}
				return obj;
			});

		});

	}

}
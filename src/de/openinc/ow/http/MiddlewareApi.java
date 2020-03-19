package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.path;
import static spark.Spark.post;

import java.util.Collection;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.core.api.analytics.AnalyticsService;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;
import spark.Request;
import spark.Response;

public class MiddlewareApi implements OpenWareAPI {
	/**
	 * API constant to get live data for specific user and sensor
	 */
	public static final String LIVE_DATA_API = "/live/:source/:sensorid";

	/**
	 * API constant to get all the registered sensor items for the user
	 */
	public static final String HISTORICAL_DATA_API = "/historical/:source/:sensorid/:timestampStart/:timestampEnd";
	/**
	 * API constant to delete device data
	 */
	public static final String DELETE_DEVICE_DATA = "/delete/:source/:sensorid/:timestampStart/:timestampEnd";
	/**
	 * API constant to get all the registered sensor items for the user
	 */
	public static final String ITEMS_API = "/items";

	/**
	 * API constant to get all the registered sensor items for the user
	 */
	public static final String ACTUATOR_TRIGGER_API = "/actuators/:aaid";
	public static final String ACTUATOR_LIST_API = "/actuators";

	/**
	 * API constant to push data via HTTP
	 */
	public static final String PUSH_DATA = "/add/:extID";

	// Status Codes
	public static final int SUCCESS_CODE = 200;
	public static final int NOT_FOUND_CODE = 404;
	public static final int CONFLICT_CODE = 409;
	public static final int INVALID_PARAMETERS = 422;
	public static final int UNAUTHORIZED_CODE = 401;

	@Override
	public void registerRoutes() {

		path("/data", () -> {

			post(PUSH_DATA, (req, res) -> {
				if (Config.accessControl) {
					User user = req.session().attribute("user");
					String strUser = req.params("source");
					JSONObject obj = Config.mapId(req.params("extID"));
					String id = obj.getString("id");
					if (user == null || !user.canAccessWrite(strUser, id))
						halt(403, "Not allowed to add data");
				}
				String id = req.params("extID");
				String data = req.body();
				DataService.onNewData(id, data);
				res.status(200);
				return SUCCESS_CODE;
			});

			post(ACTUATOR_TRIGGER_API, (req, res) -> {
				// TODO: Implement Access Control
				/*
				 * if(Config.accessControl) { User user =req.session().attribute("user"); String
				 * strUser= req.params("source"); JSONObject obj=
				 * Config.mapId(req.params("extID")); String id = obj.getString("id");
				 * if(user==null||!user.canAccessWrite(strUser, id)) halt(403,
				 * "Not allowed to add data"); }
				 */
				String aaid = req.params("aaid");
				JSONObject data = new JSONObject(req.body());
				boolean validParameter = data.has("address") && data.has("target") && data.has("payload");
				if (validParameter && DataService.sendData(aaid, data.optString("address"), data.optString("target"),
						data.optString("payload"))) {
					res.status(200);
					return SUCCESS_CODE;
				}
				res.status(INVALID_PARAMETERS);
				return "Parameters must contain 'address', 'target' & 'payload'";
			});

			path(ITEMS_API, () -> {

				get("/:user/:source", (req, res) -> {
					return getItems(req, res, req.params("source"));
				});
				get("/:source", (req, res) -> {
					return getItems(req, res, req.params("source"));
				});
				get("/", (req, res) -> {
					return getItems(req, res, null);
				});
			});

			get(LIVE_DATA_API, (req, res) -> {
				if (Config.accessControl) {
					User user = req.session().attribute("user");
					String strUser = req.params("userid");
					String strID = req.params("sensorid");
					if (user == null || !user.canAccessRead(strUser, strID))
						halt(403, "Not allowed to read data");
				}

				try {
					res.header("Content-Encoding", "gzip");
					String sensorID = req.params("sensorid");
					String userID = req.params("userid");
					OpenWareInstance.getInstance()
							.logDebug("Received live data request for sensor: " + sensorID +
										" and userID: " +
										userID);

					OpenWareDataItem items;
					if (sensorID.startsWith(Config.analyticPrefix)) {
						OpenWareInstance.getInstance()
								.logDebug("Received live analytics data request for sensor: " + sensorID +
											" and userID: " +
											userID);
						items = AnalyticsService.getInstance().handle(userID, sensorID);
					} else {
						OpenWareInstance.getInstance().logDebug(
								"Received live data request for sensor: " + sensorID +
																" and userID: " +
																userID);

						items = DataService.getLiveSensorData(sensorID, userID);
					}
					if (items == null) {
						return new JSONObject();
					} else {
						return items;
					}

				} catch (Exception e) {
					String errorRoute = "";
					for (String param : req.params().keySet()) {
						errorRoute += param + ":" +
										req.queryParams(param) +
										"\n";
					}
					;
					OpenWareInstance.getInstance()
							.logError("GET LIVE DATA REQUEST ERROR\n" + errorRoute +
										e.getLocalizedMessage(),
									e);
					return null;
				}

			});

			get(HISTORICAL_DATA_API, (req, res) -> {
				String source = req.params("source");
				String sensorid = req.params("sensorid");
				if (Config.accessControl) {
					User user = req.session().attribute("user");
					if (user == null || !user.canAccessRead(source, sensorid))
						halt(403, "Not allowed to read data");
				}

				res.header("Content-Encoding", "gzip");
				Long timestampStart = Long.valueOf(req.params("timestampStart"));
				Long timestampEnd = Long.valueOf(req.params("timestampEnd"));
				OpenWareDataItem items;
				if (sensorid.startsWith(Config.analyticPrefix)) {
					OpenWareInstance.getInstance().logDebug(
							"Received analytics data request for sensor: " + sensorid +
															" and source: " +
															source);
					items = AnalyticsService.getInstance().handle(source, sensorid, timestampStart, timestampEnd);
				} else {
					OpenWareInstance.getInstance().logDebug(
							"Received historical data request for sensor: " + sensorid +
															" and source: " +
															source);
					if (req.queryParams().size() > 0) {

						items = DataService.getHistoricalSensorData(sensorid, source, timestampStart, timestampEnd,
								req.queryMap());
					} else {
						items = DataService.getHistoricalSensorData(sensorid, source, timestampStart, timestampEnd);
					}

				}
				if (items == null) {
					return new JSONObject();
				} else {
					return items;
				}
			});

			get(DELETE_DEVICE_DATA, (req, res) -> {
				if (Config.accessControl) {
					User user = req.session().attribute("user");
					String strUser = req.params("userid");
					String strID = req.params("sensorid");
					if (user == null || !user.canAccessDelete(strUser, strID))
						halt(403, "Not allowed to delete data");
				}

				try {
					res.header("Content-Encoding", "gzip");
					String deviceID = req.params("sensorid");
					String userID = req.params("userid");
					Long timestampStart = Long.valueOf(req.params("timestampStart"));
					Long timestampEnd = Long.valueOf(req.params("timestampEnd"));
					OpenWareInstance.getInstance().logDebug(
							"Received delete device data request for device: " + deviceID +
															" and user: " +
															userID);

					if (Boolean.valueOf(Config.allowDeleteData)) {

						return new JSONObject();
					} else {
						OpenWareInstance.getInstance().logError(
								"Delete data requests have to be explicitly allowed in the server settings; setting is false or missing, so nothing will be deleted.");
						res.status(UNAUTHORIZED_CODE);
						return false;
					}
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("DELETE DATA REQUEST" + e.getLocalizedMessage(), e);
					return null;
				}

			});

		});

	}

	private Object getItems(Request req, Response res, String filter) {
		User user = null;
		if (Config.accessControl) {
			user = req.session().attribute("user");
			if (user == null)
				halt(403, "Not allowed to read data");
		}
		try {
			res.type("application/json");
			res.header("Content-Encoding", "gzip");
			String source = req.params("source");
			OpenWareInstance.getInstance()
					.logDebug("Received getItems request for userid: " + source +
								" and filtered by " +
								filter);

			Collection<OpenWareDataItem> items = DataService.getItems(user);
			if (filter != null) {
				Iterator<OpenWareDataItem> it = items.iterator();
				while (it.hasNext()) {
					OpenWareDataItem item = it.next();
					if (!item.getUser().equals(filter)) {
						it.remove();
					}
				}
			}

			if (items == null || items.size() == 0) {
				return new JSONArray();
			} else {
				return items;
			}

		} catch (Exception e) {
			String errorRoute = "";
			for (String param : req.params().keySet()) {
				errorRoute += param + ":" +
								req.queryParams(param) +
								"\n";
			}
			;
			OpenWareInstance.getInstance().logError("GET ITEMS REQUEST ERROR \n" + errorRoute +
													e.getLocalizedMessage(),
					e);
			return null;
		}
	}

}

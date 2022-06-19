package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.path;
import static spark.Spark.post;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.AnalyticsService;
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
	public static final String DELETE_DEVICE_DATA = "/items/:source/:sensorid/:timestampStart/:timestampEnd";
	/**
	 * API constant to get all the registered sensor items for the user
	 */
	public static final String ITEMS_API = "/items";

	/**
	 * API constant to get all the registered actuator items for the user
	 */
	public static final String ACTUATOR_TRIGGER_API = "/actuators/:aaid";
	public static final String ACTUATOR_LIST_API = "/actuators";

	/**
	 * API constant to push data via HTTP
	 */
	public static final String PUSH_DATA = "/push";
	public static final String UPDATE_DATA = "/update";

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
				OpenWareDataItem item = OpenWareDataItem.fromJSON(req.body());
				if (Config.getBool("accessControl", true)) {
					User user = req.session().attribute("user");
					if (user == null || !user.canAccessWrite(item.getSource(), item.getId()))
						return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
								"No write permission for source/sensor");
				}
				DataService.onNewData(item);
				res.status(200);
				return SUCCESS_CODE;
			});
			post(UPDATE_DATA, (req, res) -> {
				try {
					OpenWareDataItem update = OpenWareDataItem.fromJSON(req.body());
					if (Config.getBool("accessControl", true)) {
						User user = req.session().attribute("user");
						if (user == null || !user.canAccessWrite(update.getSource(), update.getId()))
							return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
									"No write permission for source/sensor");
					}
					int count = DataService.updateData(update);
					JSONObject result = new JSONObject();
					result.put("updateCount", count);

					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_OK, result, null);

				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null, e);
				}

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
				User user = req.session().attribute("user");
				String source = req.params("source");
				String strID = req.params("sensorid");

				if (user == null || !user.canAccessRead(source, strID)) {
					HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not Allowed to read data");
					return null;
				}
				res.header("Content-Encoding", "gzip");
				res.type("application/json");
				OpenWareDataItem result;
				try {

					long at = req.queryMap("at").longValue();
					int elements = req.queryMap("values").integerValue();
					result = DataService.getLiveSensorData(strID, source, at, elements);
				} catch (Exception e) {
					result = DataService.getLiveSensorData(strID, source);
				}
				if (result != null) {
					res.status(200);
					streamResponse(res, result);
					return null;
				} else {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
							"Data item not found");
				}

				/*
				 * if (Config.accessControl) { User user = req.session().attribute("user");
				 * String source = req.params("source"); String strID = req.params("sensorid");
				 * if (user == null || !user.canAccessRead(source, strID)) halt(403,
				 * "Not allowed to read data"); }
				 * 
				 * 
				 * String sensorID = req.params("sensorid"); String source =
				 * req.params("source"); OpenWareInstance.getInstance()
				 * .logDebug("Received live data request for sensor: " + sensorID +
				 * " and source: " + source);
				 * 
				 * OpenWareDataItem items; if (sensorID.startsWith(Config.analyticPrefix)) {
				 * OpenWareInstance.getInstance()
				 * .logDebug("Received live analytics data request for sensor: " + sensorID +
				 * " and userID: " + source); items =
				 * AnalyticsService.getInstance().handle(source, sensorID); } else {
				 * OpenWareInstance.getInstance().logDebug(
				 * "Received live data request for sensor: " + sensorID + " and userID: " +
				 * source);
				 * 
				 * items = DataService.getLiveSensorData(sensorID, source); } if (items == null)
				 * { return new JSONObject(); } else { return items; }
				 */
			});

			get(HISTORICAL_DATA_API, (req, res) -> {
				String source = req.params("source");
				String sensorid = req.params("sensorid");
				if (Config.getBool("accessControl", true)) {
					User user = req.session().attribute("user");
					if (user == null || !user.canAccessRead(source, sensorid))
						halt(403, "Not allowed to read data");
				}

				res.header("Content-Encoding", "gzip");
				res.type("application/json");
				Long timestampStart = Long.valueOf(req.params("timestampStart"));
				Long timestampEnd = Long.valueOf(req.params("timestampEnd"));
				OpenWareDataItem item;
				if (sensorid.startsWith(Config.get("analyticPrefix", "analytic."))) {
					OpenWareInstance.getInstance().logDebug(
							"Received analytics data request for sensor: " + sensorid + " and source: " + source);
					item = AnalyticsService.getInstance().handle(source, sensorid, timestampStart, timestampEnd);
				} else {
					OpenWareInstance.getInstance().logDebug(
							"Received historical data request for sensor: " + sensorid + " and source: " + source);
					if (req.queryParams().size() > 0) {

						item = DataService.getHistoricalSensorData(sensorid, source, timestampStart, timestampEnd,
								req.queryMap());
					} else {
						item = DataService.getHistoricalSensorData(sensorid, source, timestampStart, timestampEnd);
					}

				}
				if (item == null) {
					return new JSONObject();
				} else {
					res.status(200);
					streamResponse(res, item);
					return null;
				}
			});

			// TODO: Add References
			delete(DELETE_DEVICE_DATA, (req, res) -> {
				String source = req.params("source");
				String sensorid = req.params("sensorid");
				if (source == null || sensorid == null || source.equals("") || sensorid.equals("")) {
					HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
							"Missing source/sensorid parameter");
				}
				if (Config.getBool("accessControl", true)) {
					User user = req.session().attribute("user");
					if (user == null || !user.canAccessDelete(source, sensorid)) {
						HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
								"Not allowed to delete data");
						return null;
					}
				}

				try {
					res.header("Content-Encoding", "gzip");
					res.type("application/json");
					try {
						long timestampStart = Long.valueOf(req.params("timestampStart"));
						long timestampEnd = Long.valueOf(req.params("timestampEnd"));
						OpenWareInstance.getInstance().logDebug("Received delete device data request for device: "
								+ sensorid + " of source: " + source);

						if (Config.getBool("allowDeleteData", false)) {
							boolean success = DataService.deleteDeviceData(sensorid, source, timestampStart,
									timestampEnd, null);
							if (success) {
								return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_OK,
										"Successfully deleted data", null);
							} else {
								return HTTPResponseHelper.generateResponse(res,
										HTTPResponseHelper.STATUS_INTERNAL_ERROR, null,
										"Unknown error while deleting data");
							}

						} else {
							OpenWareInstance.getInstance().logError(
									"Delete data requests have to be explicitly allowed in the server settings; setting is false or missing, so nothing will be deleted.");
							return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
									"Delete data requests have to be explicitly allowed in the server settings; setting is false or missing, so nothing will be deleted.");

						}
					} catch (NumberFormatException e) {
						HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
								"Start and end timestamps need to be unix milliseconds timestamp");
						return null;
					}
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("DELETE DATA REQUEST" + e.getLocalizedMessage(), e);
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null,
							e.getLocalizedMessage());
				}

			});

		});

	}

	private Object getItems(Request req, Response res, String filter) {
		User user = null;
		if (Config.getBool("accessControl", true)) {
			user = req.session().attribute("user");
			if (user == null)
				halt(403, "Not allowed to read data");
		}

		res.type("application/json");
		res.header("Content-Encoding", "gzip");
		String source = req.params("source");
		OpenWareInstance.getInstance()
				.logDebug("Received getItems request for userid: " + source + " and filtered by " + filter);

		Collection<OpenWareDataItem> items = DataService.getItems(user);
		if (filter != null) {
			Iterator<OpenWareDataItem> it = items.iterator();
			while (it.hasNext()) {
				OpenWareDataItem item = it.next();
				if (!item.getSource().equals(filter)) {
					it.remove();
				}
			}
		}

		if (items == null || items.size() == 0) {
			return new JSONArray();
		} else {
			return items;
		}

	}

	private void streamResponse(Response res, OpenWareDataItem item) throws IOException {

		ServletOutputStream writer = res.raw().getOutputStream();
		BufferedOutputStream bout = new BufferedOutputStream(writer);
		GZIPOutputStream outZip = new GZIPOutputStream(bout);
		item.streamPrint(outZip);
		bout.flush();

	}

}

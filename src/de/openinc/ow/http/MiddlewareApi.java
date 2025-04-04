package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.JsonArray;
import de.openinc.api.ActuatorAdapter;
import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.MethodNotAllowedResponse;
import io.javalin.validation.ValidationException;
import jakarta.servlet.ServletOutputStream;

public class MiddlewareApi implements OpenWareAPI {
	/**
	 * API constant to get live data for specific source and sensor
	 */
	public static final String LIVE_DATA_API = "/live/{source}/{sensorid}";

	/**
	 * API constant to GET/POST Sensor Config
	 */
	public static final String SENSOR_CONFIG = "/items";

	/**
	 * API constant to get sensor data for the source and sensor
	 */
	public static final String HISTORICAL_DATA_API =
			"/historical/{source}/{sensorid}/{timestampStart}/{timestampEnd}";

	public static final String COUNT_DATA_API =
			"/count/{source}/{sensorid}/{timestampStart}/{timestampEnd}";
	/**
	 * API constant to get all scheduled deletes
	 */
	public static final String AUTODELETE_DATA_API = "/deletes/{source}/{sensorid}";
	/**
	 * API constant to delete device data
	 */
	public static final String DELETE_DEVICE_DATA =
			"/items/{source}/{sensorid}/{timestampStart}/{timestampEnd}";
	/**
	 * API constant to get all the registered sensor items for the user
	 */
	public static final String ITEMS_API = "/items";

	/**
	 * API constant to get all the registered actuator items for the user
	 */
	public static final String ACTUATOR_TRIGGER_API = "/{aaid}";
	public static final String ACTUATOR_LIST_API = "/actuator";

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

			post(PUSH_DATA, ctx -> {
				OpenWareDataItem item = OpenWareDataItem.fromJSON(ctx.body());
				if (Config.getBool("accessControl", true)) {
					User user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessWrite(item.getSource(), item.getId()))
						HTTPResponseHelper.forbidden("No write permission for source/sensor");

				}
				DataService.onNewData(item);
				HTTPResponseHelper.ok(ctx, "Received data");

			});
			post(UPDATE_DATA, ctx -> {
				try {
					OpenWareDataItem update = OpenWareDataItem.fromJSON(ctx.body());
					if (Config.getBool("accessControl", true)) {
						User user = ctx.sessionAttribute("user");
						if (user == null
								|| !user.canAccessWrite(update.getSource(), update.getId()))
							HTTPResponseHelper.forbidden("No write permission for source/sensor");
					}
					int count = DataService.updateData(update);
					JSONObject result = new JSONObject();
					result.put("updateCount", count);

					HTTPResponseHelper.ok(ctx, result);

				} catch (JSONException e) {
					HTTPResponseHelper.badRequest("Error while updating data: " + e.getMessage());
				}

			});
			path("admin", () -> {
				post(SENSOR_CONFIG, ctx -> {

					User user = null;
					if (Config.getBool("accessControl", true)) {
						user = ctx.sessionAttribute("user");
						if (user == null)
							HTTPResponseHelper.forbidden("You need to log in to configure items");
					}
					try {

						String res = DataService.storeItemConfiguration(user, ctx.body());
						HTTPResponseHelper.ok(ctx, res);

					} catch (SecurityException | MethodNotAllowedResponse e2) {
						HTTPResponseHelper
								.forbidden("Not allowed to configure sensor\n" + e2.getMessage());

					} catch (Exception e) {
						OpenWareInstance.getInstance().logError(
								"Malformed data posted to Sensor Config API\n" + ctx.body(), e);
						HTTPResponseHelper.badRequest(
								"Malformed data posted to Sensor Config API\n" + e.getMessage());

					}
				});

				get(SENSOR_CONFIG + "/{source}", ctx -> {
					User user = ctx.sessionAttribute("user");
					String source = ctx.pathParam("source");
					if (Config.getBool("accessControl", true) && user == null) {
						HTTPResponseHelper.forbidden("You need to log in to configure items");
					}
					try {

						ctx.json(DataService.getItemConfiguration(user).values().stream()
								.filter(new Predicate<OpenWareDataItem>() {

									@Override
									public boolean test(OpenWareDataItem t) {
										return source == null || t.getSource().equals(source);
									}
								}).filter(new Predicate<OpenWareDataItem>() {

									@SuppressWarnings("null")
									@Override
									public boolean test(OpenWareDataItem t) {
										String source = t.getMeta().optString("source_source");
										String id = t.getMeta().optString("id_source");
										if (source.equals(""))
											source = t.getSource();
										if (id.equals(""))
											id = t.getId();
										return user.canAccessWrite(source, id);
									}

								}).map((item) -> {
									item.value(DataService
											.getLiveSensorData(item.getId(), item.getSource())
											.value());
									return item;
								}).collect(Collectors.toList()));
					} catch (Exception e) {
						HTTPResponseHelper.internalError(e.getMessage());
					}

				});

				get(SENSOR_CONFIG, ctx -> {
					User user = ctx.sessionAttribute("user");
					if (Config.getBool("accessControl", true) && user == null) {
						HTTPResponseHelper.forbidden("You need to log in to configure items");
					}
					ctx.json(DataService.getItemConfiguration(user).values().stream()
							.filter(new Predicate<OpenWareDataItem>() {

								@SuppressWarnings("null")
								@Override
								public boolean test(OpenWareDataItem t) {
									String source = t.getMeta().optString("source_source");
									String id = t.getMeta().optString("id_source");
									if (source.equals(""))
										source = t.getSource();
									if (id.equals(""))
										id = t.getId();
									return user.canAccessWrite(source, id);
								}

							}).collect(Collectors.toList()));

				});

				delete(SENSOR_CONFIG + "/{owner}/{sensor}", ctx -> {
					User user = null;
					if (Config.getBool("accessControl", true)) {
						user = ctx.sessionAttribute("user");
						if (user == null)
							throw new ForbiddenResponse("You need to log in to configure items");
					}
					if (DataService.deleteItemConfig(user, ctx.pathParam("owner"),
							ctx.pathParam("sensor"))) {
						ctx.json("Successfully deleted Configuration");

					} else {
						throw new BadRequestResponse(
								"Could not delete configuration or no configuration was assigned");

					}
				});
			});

			path(ITEMS_API, () -> {

				get("/{user}/{source}", ctx -> {
					getItems(ctx, Set.of(ctx.pathParam("source")));
				});
				get("/{source}", ctx -> {
					getItems(ctx, Set.of(ctx.pathParam("source")));
				});
				get("/", ctx -> {
					getItems(ctx, null);
				});
				post("/", ctx -> {
					String body = ctx.body();
					try {
						JSONArray tags = new JSONArray(body);
						List<String> taglist = tags.toList().stream().map(o -> (String) o)
								.collect(Collectors.toList());
						getItems(ctx, Set.copyOf(taglist));
					} catch (JSONException e) {
						HTTPResponseHelper.badRequest("Body must contain source-tag array");
					}

				});
			});


			get(LIVE_DATA_API, ctx -> {
				User user = ctx.sessionAttribute("user");
				String source = ctx.pathParam("source");
				String strID = ctx.pathParam("sensorid");

				if (user == null || !user.canAccessRead(source, strID)) {
					HTTPResponseHelper.forbidden("Not Allowed to read data");
				}
				OpenWareDataItem result;
				try {

					long at = ctx.queryParamAsClass("at", Long.class).get();
					int elements = ctx.queryParamAsClass("values", Integer.class).get();
					result = DataService.getLiveSensorData(strID, source, at, elements);
				} catch (Exception e) {
					result = DataService.getLiveSensorData(strID, source);
				}
				if (result != null) {
					streamResponse(ctx, result);
					ctx.status(200);

				} else {
					HTTPResponseHelper.badRequest("Data item not found");
				}


			});

			get(COUNT_DATA_API, ctx -> {
				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessRead(source, sensorid))
						HTTPResponseHelper.forbidden("Not allowed to read data");
				}

				Long timestampStart = ctx.pathParamAsClass("timestampStart", Long.class).get();
				Long timestampEnd = ctx.pathParamAsClass("timestampEnd", Long.class).get();
				OpenWareDataItem item;
				OpenWareDataItem count = DataService.countSensorData(sensorid, source,
						timestampStart, timestampEnd, null, null);
				if (count == null) {
					HTTPResponseHelper.ok(ctx, Double.valueOf(0));
				}
				double countOfValues = (double) count.value().get(0).get(0).value();
				HTTPResponseHelper.ok(ctx, Double.valueOf(countOfValues));
			});

			get(HISTORICAL_DATA_API, ctx -> {

				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessRead(source, sensorid))
						HTTPResponseHelper.forbidden("Not allowed to read data");
				}

				Long timestampStart = ctx.pathParamAsClass("timestampStart", Long.class).get();
				Long timestampEnd = ctx.pathParamAsClass("timestampEnd", Long.class).get();
				OpenWareDataItem item;

				if (ctx.queryParamMap().size() > 0) {

					item = DataService.getHistoricalSensorData(sensorid, source, timestampStart,
							timestampEnd, ctx.queryParamMap(), user);
					if (item == null) {
						HTTPResponseHelper.internalError(String.format(
								"Could not get historical data for [%s]%s", source, sensorid));
					}
					streamResponse(ctx, item);
					ctx.status(200);
				} else {
					OpenWareDataItem count = DataService.countSensorData(sensorid, source,
							timestampStart, timestampEnd, null, null);
					boolean isAnalytic =
							sensorid.startsWith(Config.get("analyticPrefix", "analytics."));
					if (count == null && !isAnalytic) {
						HTTPResponseHelper.badRequest("No Data for request");

					}
					double countOfValues =
							isAnalytic ? 1 : (double) count.value().get(0).get(0).value();
					if (countOfValues < 1_00_000) {
						item = DataService.getHistoricalSensorData(sensorid, source, timestampStart,
								timestampEnd);
						ctx.result(item.asInputStream());
						ctx.status(200);
					} else {
						long interval = timestampEnd - timestampStart;
						int steps = (int) countOfValues / 100000;
						int stepSize = (int) interval / steps;
						PipedInputStream in = new PipedInputStream();
						PipedOutputStream out = new PipedOutputStream(in);
						ctx.contentType("application/json");
						OutputStream outputStream = ctx.res().getOutputStream();

						for (int i = 0; i < steps; i++) {
							long start = timestampStart + (stepSize * i);
							long end = i == steps - 1 ? timestampEnd
									: (timestampStart + (stepSize * (i + 1))) - 1;
							item = DataService.getHistoricalSensorData(sensorid, source, start,
									end);
							boolean last = i == (steps - 1);
							boolean valuesOnly = i != 0;

							item.streamPrint(outputStream, valuesOnly, last);
						}


						ctx.status(200);
					}

				}

			});

			// TODO: Add References
			delete(DELETE_DEVICE_DATA, ctx -> {
				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				if (source == null || sensorid == null || source.equals("")
						|| sensorid.equals("")) {
					HTTPResponseHelper.badRequest("Missing source/sensorid parameter");
				}
				if (Config.getBool("accessControl", true)) {
					User user = ctx.sessionAttribute("user");
					if (user == null || !user.canAccessDelete(source, sensorid)) {
						HTTPResponseHelper.forbidden("Not allowed to delete data");
					}
				}

				try {
					long timestampStart = ctx.pathParamAsClass("timestampStart", Long.class).get();
					long timestampEnd = ctx.pathParamAsClass("timestampEnd", Long.class).get();
					OpenWareInstance.getInstance()
							.logDebug("Received delete device data request for device: " + sensorid
									+ " of source: " + source);

					if (Config.getBool("allowDeleteData", false)) {
						boolean success = DataService.deleteDeviceData(sensorid, source,
								timestampStart, timestampEnd, null);
						if (success) {
							HTTPResponseHelper.ok(ctx, "Successfully deleted data");
						} else {
							HTTPResponseHelper.internalError("Unknown error while deleting data");
						}

					} else {
						OpenWareInstance.getInstance().logError(
								"Delete data requests have to be explicitly allowed in the server settings; setting is false or missing, so nothing will be deleted.");
						HTTPResponseHelper.forbidden(
								"Delete data requests have to be explicitly allowed in the server settings; setting is false or missing, so nothing will be deleted.");

					}
				} catch (ValidationException e) {
					HTTPResponseHelper.badRequest(
							"Start and end timestamps need to be unix milliseconds timestamp");

				} catch (Exception e) {
					HTTPResponseHelper.internalError(e.getMessage());
				}

			});

			get(AUTODELETE_DATA_API, (ctx) -> {
				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				User user = ctx.sessionAttribute("user");
				if (user == null || !user.canAccessRead(source, sensorid)) {
					HTTPResponseHelper
							.forbidden("Not allowed to view scheduled deletes for sensor");
				}

				try {
					HTTPResponseHelper.ok(ctx, DataService.getScheduledDeletes(source, sensorid));
				} catch (Exception e) {
					HTTPResponseHelper
							.internalError("Could not get schedule deletes.\n" + e.getMessage());
				}

			});
			delete(AUTODELETE_DATA_API, (ctx) -> {
				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				User user = ctx.sessionAttribute("user");
				if (user == null || !user.canAccessRead(source, sensorid)) {
					HTTPResponseHelper
							.forbidden("Not allowed to change scheduled deletes for sensor");
				}

				try {
					DataService.unscheduleDeletes(source, sensorid);
					HTTPResponseHelper.ok(ctx, "Unscheduling successful");
				} catch (Exception e) {
					HTTPResponseHelper
							.internalError("Could not get schedule deletes.\n" + e.getMessage());
				}

			});

			post(AUTODELETE_DATA_API, (ctx) -> {
				String source = ctx.pathParam("source");
				String sensorid = ctx.pathParam("sensorid");
				User user = ctx.sessionAttribute("user");
				if (user == null || !user.canAccessWrite(source, sensorid)) {
					HTTPResponseHelper.forbidden("Not allowed to schedule delete for sensor");
				}

				try {
					JSONObject body = new JSONObject(ctx.body());
					long seconds = body.getLong("secondsToLive");

					try {
						DataService.scheduleDelete(source, sensorid, seconds);
						HTTPResponseHelper.ok(ctx, "Deletion successfully scheduled");
					} catch (Exception e) {
						HTTPResponseHelper.internalError(
								"Could not get schedule deletes.\n" + e.getMessage());
					}

				} catch (JSONException e) {
					HTTPResponseHelper.badRequest(
							"Malformed data posted to Schedule Delete API. Please provide body as JSON object containing key 'secondsToLive'");
				}

			});

		});



		path(ACTUATOR_LIST_API, () -> {
			get("/", ctx -> {
				HTTPResponseHelper.ok(ctx, DataService.getActuators());
			});
			post(ACTUATOR_TRIGGER_API, ctx -> {
				User user = ctx.sessionAttribute("user");
				String aaid = ctx.pathParam("aaid");
				ActuatorAdapter adapter = DataService.getActuator(aaid);

				if (adapter == null) {
					HTTPResponseHelper.badRequest("Actuator not found");
					return;
				}

				JSONObject body = new JSONObject(ctx.body());
				String target = body.getString("target");
				String topic = body.getString("topic");
				String payload = body.getString("payload");
				String optionsString = body.optString("options", "{}");
				JSONObject options = new JSONObject(optionsString);
				if (!options.has("templateType")) {
					options.put("templateType", "vtl");
				}
				try {
					CompletableFuture<Object> res = ((CompletableFuture<Object>) adapter
							.send(target, topic, payload, user, options, new ArrayList()));
					ctx.future(() -> {
						return res.thenAccept((object) -> {
							HTTPResponseHelper.ok(ctx, object);
						}).exceptionally(error -> {
							HTTPResponseHelper.internalError(error.getMessage());
							return null;
						});
					});
				} catch (Exception e) {
					HTTPResponseHelper.internalError(e.getMessage());
					return;
				}



				// HTTPResponseHelper.ok(ctx, res);
				// ctx.status(200);
			});
		});
	}

	private void getItems(Context ctx, Set<String> filter) {
		User user = null;
		if (Config.getBool("accessControl", true)) {
			user = ctx.sessionAttribute("user");
			if (user == null) {
				HTTPResponseHelper.forbidden("You need to sign in to retrieve items");
			}

		}

		OpenWareInstance.getInstance().logDebug("Received getItems request for "
				+ (filter == null ? "all" : filter.size()) + " source(s)");

		Collection<OpenWareDataItem> items = DataService.getItems(user, filter);

		if (items == null || items.size() == 0) {
			ctx.json(new JsonArray());
		} else {
			ctx.json(items);
		}

	}

	private void streamResponse(Context ctx, OpenWareDataItem item) throws IOException {
		streamResponse(ctx, item, false, true);
	};

	private void streamResponse(Context ctx, OpenWareDataItem item, boolean valuesOnly,
			boolean last) throws IOException {

		ServletOutputStream writer = ctx.outputStream();
		BufferedOutputStream bout = new BufferedOutputStream(writer);
		item.streamPrint(bout, valuesOnly, last);
		bout.flush();

	}

}

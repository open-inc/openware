package de.openinc.ow.http;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.path;
import static spark.Spark.post;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.ServletOutputStream;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
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
	public static final String GET_LOGS = "/logs";

	/**
	 * API constant to GET/POST Sensor Config
	 */
	public static final String SENSOR_CONFIG = "/sensors";

	@Override
	public void registerRoutes() {

		path("/admin", () -> {
			get(GET_LOGS, (req, res) -> {
				ServletOutputStream out = res.raw().getOutputStream();
				int files = 1;
				String type = "console";
				if (req.queryParams().size() > 0) {
					files = Integer.parseInt(req.queryMap().value("files"));
					type = req.queryMap().value("type");
					if (type == null || type.equals("")) {
						type = "console";
					}
				}
				final String filterType = type.toLowerCase();
				File logDir = new File("logs");
				if (!logDir.isDirectory()) {
					out.write("Can't open Log Dir".getBytes());
					return null;
				}
				List<String> filteredFiles = Arrays.stream(logDir.list(new FilenameFilter() {

					@Override
					public boolean accept(File dir, String name) {
						// TODO Auto-generated method stub
						return name.toLowerCase().contains(filterType);
					}
				})).sorted(new Comparator<String>() {

					@Override
					public int compare(String o1, String o2) {
						File f1 = new File("logs" + File.separatorChar + o1);
						File f2 = new File("logs" + File.separatorChar + o2);
						long mf1 = f1.lastModified();
						long mf2 = f2.lastModified();
						if (mf1 > mf2)
							return -1;
						if (mf1 < mf2)
							return 1;
						return 0;
					}
				}).limit(files).collect(Collectors.toList());

				BufferedOutputStream bout = new BufferedOutputStream(out);
				for (String file : filteredFiles) {
					File f = new File("logs" + File.separatorChar + file);
					System.out.println("Printing logs " + file + "(" + f.getAbsolutePath() + ")");
					FileInputStream fis = new FileInputStream(f);
					List<String> lines = IOUtils.readLines(fis, Charset.forName("UTF-8"));
					res.status(200);
					for (String line : lines) {
						bout.write((line + "\n").getBytes());
					}
				}
				bout.flush();
				return null;
			});

			post(SENSOR_CONFIG, (req, res) -> {

				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = req.session().attribute("user");
					if (user == null)
						return HTTPResponseHelper.generateResponse(res, 403, null,
								"You need to log in to configure items");
				}
				try {
					if (DataService.storeItemConfiguration(user, req.body())) {
						return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_OK,
								"Stored configuration", null);
					} else {
						return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null,
								"Could not store configuration");
					}

				} catch (org.json.JSONException e) {
					OpenWareInstance.getInstance().logError("Malformed data posted to Sensor Config API\n" + req.body(),
							e);
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_BAD_REQUEST, null,
							"Malformed data posted to Sensor Config API\n" + e.getMessage());

				} catch (SecurityException e2) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_FORBIDDEN, null,
							"Not allowed to configure sensor\n" + e2.getMessage());

				}
			});

			get(SENSOR_CONFIG + "/:source", (req, res) -> {
				User user = null;
				String source = req.params("source");
				if (Config.getBool("accessControl", true)) {
					user = req.session().attribute("user");
					if (user == null)
						return HTTPResponseHelper.generateResponse(res, 403, null,
								"You need to log in to configure items");
				}
				try {

					return DataService.getItemConfiguration(user).values().stream()
							.filter(new Predicate<OpenWareDataItem>() {

								@Override
								public boolean test(OpenWareDataItem t) {
									return source == null || t.getSource().equals(source);
								}
							}).collect(Collectors.toList());
				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, 400, null, e.getMessage());
				}

			});

			get(SENSOR_CONFIG, (req, res) -> {
				User user = null;
				String source = req.params("source");
				if (Config.getBool("accessControl", true)) {
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
				if (Config.getBool("accessControl", true)) {
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
					return "No Permission \n" + e2.getMessage() + "\n" + req.body();
				}
			});

			get(GET_STATS, (req, res) -> {

				return HTTPResponseHelper.generateResponse(res, 200, OpenWareInstance.getInstance().getState(), null);

			});

			get(GET_ANALYTIC_STATS, (req, res) -> {
				JSONObject obj = new JSONObject();
				// TODO: Replace Analytic sensor
				return obj;
			});

		});

	}

}
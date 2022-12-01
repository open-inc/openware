package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;

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
			get(GET_LOGS, ctx -> {
				int files = 1;
				String type = "console";
				if (ctx.queryParamMap().size() > 0) {
					files = Integer.parseInt(ctx.queryParam("files"));
					type = ctx.queryParam("type");
					if (type == null || type.equals("")) {
						type = "console";
					}
				}
				final String filterType = type.toLowerCase();
				File logDir = new File("logs");
				if (!logDir.isDirectory()) {
					throw new InternalServerErrorResponse("Can't open log dir");
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
				ctx.status(200);
				OutputStream out = ctx.outputStream();
				BufferedOutputStream bout = new BufferedOutputStream(out);
				for (String file : filteredFiles) {
					File f = new File("logs" + File.separatorChar + file);
					System.out.println("Printing logs " + file + "(" + f.getAbsolutePath() + ")");
					FileInputStream fis = new FileInputStream(f);
					List<String> lines = IOUtils.readLines(fis, Charset.forName("UTF-8"));
					for (String line : lines) {
						bout.write((line + "\n").getBytes());
					}
				}
				bout.flush();
				bout.close();
			});

			post(SENSOR_CONFIG, ctx -> {

				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null)
						HTTPResponseHelper.forbidden("You need to log in to configure items");
				}
				try {
					if (DataService.storeItemConfiguration(user, ctx.body())) {

						HTTPResponseHelper.ok(ctx, "Stored configuration");
					} else {
						HTTPResponseHelper.internalError("Could not store configuration");
					}

				} catch (org.json.JSONException e) {
					OpenWareInstance.getInstance().logError("Malformed data posted to Sensor Config API\n" + ctx.body(),
							e);
					HTTPResponseHelper.badRequest("Malformed data posted to Sensor Config API\n" + e.getMessage());

				} catch (SecurityException e2) {
					HTTPResponseHelper.forbidden("Not allowed to configure sensor\n" + e2.getMessage());

				}
			});

			get(SENSOR_CONFIG + "/{source}", ctx -> {
				User user = null;
				String source = ctx.pathParam("source");
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null)
						HTTPResponseHelper.forbidden("You need to log in to configure items");
				}
				try {

					ctx.json(DataService.getItemConfiguration(user).values().stream()
							.filter(new Predicate<OpenWareDataItem>() {

								@Override
								public boolean test(OpenWareDataItem t) {
									return source == null || t.getSource().equals(source);
								}
							}).collect(Collectors.toList()));
				} catch (Exception e) {
					HTTPResponseHelper.internalError(e.getMessage());
				}

			});

			get(SENSOR_CONFIG, ctx -> {
				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null)
						HTTPResponseHelper.forbidden("You need to log in to configure items");
				}
				ctx.json(DataService.getItemConfiguration(user).values());

			});

			delete(SENSOR_CONFIG + "/{owner}/{sensor}", ctx -> {
				User user = null;
				if (Config.getBool("accessControl", true)) {
					user = ctx.sessionAttribute("user");
					if (user == null)
						throw new ForbiddenResponse("You need to log in to configure items");
				}
				if (DataService.deleteItemConfig(user, ctx.pathParam("owner"), ctx.pathParam("sensor"))) {
					ctx.json("Successfully deleted Configuration");

				} else {
					throw new BadRequestResponse("Could not delete configuration or no configuration was assigned");

				}
			});

			get(GET_STATS, ctx -> {

				HTTPResponseHelper.ok(ctx, OpenWareInstance.getInstance().getState());

			});

		});

	}

}
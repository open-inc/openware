package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.before;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.sse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.math3.analysis.function.Log;
import org.json.JSONObject;
import de.openinc.api.AdminFunctionInterface;
import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.Role;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.helper.LogConsumer;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;


public class AdminAPI implements OpenWareAPI {
	/**
	 * API constants to use admin APIs
	 */
	public static final String GET_STATS = "/state";
	public static final String GET_ANALYTIC_STATS = "/getAnalyticsStats";
	public static final String GET_LOGS = "/logs";
	public static final String GET_LOGS_LIVE = "/livelogs";
	public static final String GET_ADMIN_FUNCTIONS = "/functions";
	public static final String POST_ADMIN_FUNCTIONS = "/functions/{functionName}";


	private static void checkAdminRole(Context ctx) throws ForbiddenResponse {
		User user = ctx.sessionAttribute("user");
		String roleLabel = Config.get("OW_ADMIN_ROLE", "od-admin");
		Optional<Role> adminRole = user.getRoles().stream()
				.filter(role -> role.getName().equals(roleLabel)).findFirst();
		if (adminRole.isEmpty()) {
			HTTPResponseHelper.forbidden("User is no " + roleLabel);
		}
	}

	private static List<Role> checkRoles(Context ctx, List<Role> roles) throws ForbiddenResponse {
		User user = ctx.sessionAttribute("user");
		String roleLabel = Config.get("OW_ADMIN_ROLE", "od-admin");
		List<Role> permittedRoles = user.getRoles().stream()
				.filter(role -> role.getName().equals(roleLabel)).collect(Collectors.toList());
		if (permittedRoles.isEmpty()) {
			HTTPResponseHelper.forbidden("User is no " + roleLabel);
		}
		return permittedRoles;
	}

	private static void logs(Context ctx) throws Exception {
		checkAdminRole(ctx);
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
	};

	@Override
	public void registerRoutes() {



		path("/admin", () -> {
			// before(ctx -> {
			// User user = ctx.sessionAttribute("user");
			// String roleLabel = Config.get("OW_ADMIN_ROLE", "od-admin");
			// Optional<Role> adminRole = user.getRoles().stream()
			// .filter(role -> role.getName().equals(roleLabel)).findFirst();
			// if (adminRole.isEmpty()) {
			// HTTPResponseHelper.forbidden("User is no " + roleLabel);
			// }
			// });
			get(GET_ADMIN_FUNCTIONS, ctx -> {
				ServiceLoader.load(AdminFunctionInterface.class).forEach(adminFunction -> {
					JSONObject json = new JSONObject();
					json.put("name", adminFunction.getName());
					json.put("description", adminFunction.getDescription());
					json.put("functionName", adminFunction.getFunctionName());
					json.put("allowedRoles", adminFunction.getAllowedRoles().stream()
							.map(role -> role.getName()).collect(Collectors.toList()));
					ctx.json(json);
				});
			});
			post(POST_ADMIN_FUNCTIONS, ctx -> {

				String function = ctx.pathParam("functionName");
				JSONObject params = new JSONObject(ctx.body());
				ServiceLoader.load(AdminFunctionInterface.class).stream()
						.filter(provider -> provider.get().getFunctionName().equals(function))
						.findFirst().ifPresentOrElse(provider -> {

							AdminFunctionInterface adminFunction = provider.get();
							List<Role> roles = checkRoles(ctx, adminFunction.getAllowedRoles());
							if (roles.isEmpty()) {
								return;
							}
							try {
								Object res = adminFunction.execute(params);
								HTTPResponseHelper.ok(ctx, res);
							} catch (Exception e) {
								OpenWareInstance.getInstance().logError(
										"Error while executing admin function " + function, e);
								HTTPResponseHelper
										.internalError("Error while executing admin function "
												+ function + ":\n" + e.getMessage());
							}
						}, () -> {
							HTTPResponseHelper
									.badRequest("No function found with name " + function);

						});


			});
			get(GET_LOGS, ctx -> logs(ctx));
			sse(GET_LOGS_LIVE, client -> {
				LogConsumer logConsumer = new LogConsumer() {
					@Override
					public void onLog(String level, String message) {
						JSONObject json = new JSONObject();
						json.put("level", level);
						json.put("message", message);
						client.sendEvent(json.toString());
					}
				};
				client.onClose(() -> {
					OpenWareInstance.getInstance().unregisterLogConsumer(logConsumer);
				});
				client.close();
			});


			get(GET_STATS, ctx -> {
				checkAdminRole(ctx);
				HTTPResponseHelper.ok(ctx, OpenWareInstance.getInstance().getState());

			});

		});

	}

}

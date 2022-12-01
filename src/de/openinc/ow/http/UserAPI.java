package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.user.User;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.UserService;
import io.javalin.http.Context;

public class UserAPI implements OpenWareAPI {

	private static final String OPERATION_ME = "me";
	private static final String OPERATION_DATA = "data";
	private static final String OPERATION_LOGIN = "login";
	private static final String OPERATION_ACCESS = "access";
	private static final String OPERATION_SESSION_REFRESH = "refresh";
	public static final String OD_SESSION = "OD-SESSION";
	/**
	 * API constant to access User Management
	 */
	public static final String USER_API = "/users/{operation}";
	public static final String USER_API_OBJECT = "/users/{operation}/{oid}";
	public static final String USER_API_ACCESS_OBJECT = "/users/{operation}/{sourceid}/{sensorid}";

	private void handlePostData(Context ctx) {
		String header = ctx.header(OD_SESSION);
		JSONObject parameter = new JSONObject(ctx.body());
		parameter.put(OD_SESSION, header);
		String op = ctx.pathParam("operation");
		String key = ctx.pathParam("oid");
		switch (op) {
		case OPERATION_DATA: {
			boolean success = UserService.getInstance().storeData(header, parameter);
			if (success) {
				HTTPResponseHelper.ok(ctx, "Successfully saved value");
				break;
			} else {
				HTTPResponseHelper.badRequest("Could not save value");
				break;
			}
		}
		default: {
			HTTPResponseHelper.badRequest("Unkown User Operation");
		}
		}

	}

	private void handleUserGet(Context ctx) {
		String op = ctx.pathParam("operation");
		String header = ctx.header(OD_SESSION);

		switch (op) {
		case OPERATION_ACCESS: {
			User user = null;
			if (Config.getBool("accessControl", true)) {
				user = ctx.sessionAttribute("user");
				if (user == null)
					HTTPResponseHelper.forbidden("You need to login first");
			}

			String source = ctx.pathParam("sourceid");
			String sensor = ctx.pathParam("sensorid");
			JSONObject res = new JSONObject();
			JSONObject access = new JSONObject();
			res.put("user", user.getName());
			res.put("source", source);
			res.put("sensor", sensor);
			access.put("read", user.canAccessRead(source, sensor));
			access.put("write", user.canAccessWrite(source, sensor));
			access.put("delete", user.canAccessDelete(source, sensor));
			res.put("access", access);
			HTTPResponseHelper.ok(ctx, res);
			break;
		}
		case OPERATION_ME: {
			User user = UserService.getInstance().checkAuth(header);
			if (user != null) {
				HTTPResponseHelper.ok(ctx, user);
				break;
			} else {
				HTTPResponseHelper.ok(ctx, "The provided session is invalid");
			}
		}
		case OPERATION_LOGIN: {
			String username = ctx.queryParam("username");
			String pw = ctx.queryParam("password");
			User user = UserService.getInstance().login(username, pw);
			if (user != null) {
				HTTPResponseHelper.ok(ctx, user);
				break;
			} else {
				HTTPResponseHelper.forbidden("Invalid Credentials");

			}
		}
		case OPERATION_DATA: {
			User user = UserService.getInstance().checkAuth(header);
			if (user != null) {
				HTTPResponseHelper.ok(ctx, user.getData());
				break;
			} else {
				HTTPResponseHelper.forbidden("Not authorized");
			}
		}
		default: {
			HTTPResponseHelper.badRequest("Invalid operation");
		}
		}

	}

	private void handleSpecifcDataGet(Context ctx) {
		String op = ctx.pathParam("operation");
		String key = ctx.pathParam("oid");
		String header = ctx.header(OD_SESSION);
		switch (op) {
		case OPERATION_DATA: {
			JSONObject value = UserService.getInstance().readData(header);
			if (value != null) {
				HTTPResponseHelper.ok(ctx, value);
				break;
			} else {
				HTTPResponseHelper.badRequest("Could not retrieve data");
			}
		}
		default: {
			HTTPResponseHelper.badRequest("Invalid operation");
		}
		}

	}

	@Override
	public void registerRoutes() {
		get(USER_API, ctx -> {
			handleUserGet(ctx);
		});

		get(USER_API_OBJECT, ctx -> {
			handleSpecifcDataGet(ctx);
		});

		post(USER_API_OBJECT, ctx -> {
			handlePostData(ctx);
		});

		get(USER_API_ACCESS_OBJECT, (ctx) -> {
			handleUserGet(ctx);
		});

	}

}

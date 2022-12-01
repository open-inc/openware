package de.openinc.ow.helper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import de.openinc.ow.OpenWareInstance;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;

public class HTTPResponseHelper {
	public static int STATUS_OK = 200;
	public static int STATUS_INTERNAL_ERROR = 500;
	public static int STATUS_FORBIDDEN = 403;
	public static int STATUS_BAD_REQUEST = 400;

	public static void ok(Context ctx, Object result) {
		Gson gson = OpenWareInstance.getInstance().getGSONInstance();
		JsonObject res = new JsonObject();
		res.addProperty("status", 200);
		res.add("result", gson.toJsonTree(result));
		ctx.json(res);
		ctx.status(200);
	}

	public static void forbidden(String reason) {
		throw new ForbiddenResponse(reason);
	}

	public static void internalError(String reason) {
		throw new InternalServerErrorResponse(reason);
	}

	public static void badRequest(String reason) {
		throw new BadRequestResponse(reason);
	}
}

package de.openinc.ow.core.helper;

import org.json.JSONObject;

import spark.Response;

public class HTTPResponseHelper {
	public static int STATUS_OK = 200;
	public static int STATUS_INTERNAL_ERROR = 500;
	public static int STATUS_FORBIDDEN = 403;
	public static int STATUS_BAD_REQUEST = 400;

	public static JSONObject generateResponse(Response response, int statuscode, Object result, Object err) {
		if (err instanceof Exception) {
			err = ((Exception) err).getMessage();
		}
		JSONObject res = new JSONObject();
		res.put("status", statuscode);
		res.put("result", result);
		res.put("err", err);
		response.body(res.toString());
		response.status(statuscode);
		response.type("application/json");
		return res;
	}
}

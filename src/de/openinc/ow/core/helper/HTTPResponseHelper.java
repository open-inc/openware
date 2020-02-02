package de.openinc.ow.core.helper;

import org.json.JSONObject;

import spark.Response;

public class HTTPResponseHelper {

	public static JSONObject generateResponse(Response response, int statuscode, Object result, Object err) {
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

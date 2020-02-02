package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.post;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.core.helper.HTTPResponseHelper;
import de.openinc.ow.core.model.data.ReferenceDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;

public class ReferenceAPI implements OpenWareAPI {
	public static String SERVICE_ACCESS_ID = "SERVICE_REFERENCE";
	private static String REF_URL = "/references";

	@Override
	public void registerRoutes() {
		get(REF_URL + "/:reference", (req, res) -> {
			ReferenceDataItem rdi = DataService.getReferenceAdapter().getReferenceInfo(req.params("reference"),
					req.session().attribute("user"));
			return HTTPResponseHelper.generateResponse(res, 200, rdi.toJSON(), null);
		});
		get(REF_URL, (req, res) -> {
			JSONArray refs = new JSONArray();
			Map<String, String> cRefs = DataService.getReferenceAdapter().getCurrentReferences();
			for (String source : cRefs.keySet()) {
				JSONObject toPut = new JSONObject();
				toPut.put("source", source);
				toPut.put("reference", cRefs.get(source));
				refs.put(toPut);
			}
			return HTTPResponseHelper.generateResponse(res, 200, refs, null);

		});

		post(REF_URL + "/:refid", (req, res) -> {
			JSONObject body = new JSONObject(req.body());
			String ref = req.params("refid");
			if (!body.has("source")) {
				return HTTPResponseHelper.generateResponse(res, 422, null, "Missing source parameter");
			}
			String source = body.getString("source");
			JSONObject info = body.optJSONObject("info");
			User user = (User) req.session().attribute("user");
			if (!user.canAccessWrite(SERVICE_ACCESS_ID, source)) {
				return HTTPResponseHelper.generateResponse(res, 405, null,
						"Missing access permission to set reference for " + source);
			}
			DataService.getReferenceAdapter().setReferenceForSource(source, ref);
			return HTTPResponseHelper.generateResponse(res, 200, "Reference set for " + source, null);
		});
	}

}

package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.post;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.ReferenceDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;
import spark.QueryParamsMap;

public class ReferenceAPI implements OpenWareAPI {
	public static String SERVICE_ACCESS_ID = "SERVICE_REFERENCE";
	private static String REF_URL = "/references";

	@Override
	public void registerRoutes() {
		get(REF_URL + "/:reference/:start/:end", (req, res) -> {
			QueryParamsMap params = req.queryMap();
			if (params.hasKey("sensor") && params.hasKey("source")) {
				try {
					ReferenceDataItem rdi = DataService.getReferenceAdapter().getReferencedData(
							req.params("reference"),
							req.session().attribute("user"), params.get("source").value(), params.get("sensor").value(),
							Long.valueOf(req.params("start")),
							Long.valueOf(req.params("end")));
					return HTTPResponseHelper.generateResponse(res, 200, rdi.toJSON(), null);
				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null, e);
				}
			} else {
				try {
					ReferenceDataItem rdi = DataService.getReferenceAdapter().getAllReferencedData(
							req.params("reference"),
							req.session().attribute("user"), Long.valueOf(req.params("start")),
							Long.valueOf(req.params("end")));
					return HTTPResponseHelper.generateResponse(res, 200, rdi.toJSON(), null);
				} catch (Exception e) {
					return HTTPResponseHelper.generateResponse(res, HTTPResponseHelper.STATUS_INTERNAL_ERROR, null, e);
				}
			}

		});

		get(REF_URL + "/:reference", (req, res) -> {
			ReferenceDataItem rdi = DataService.getReferenceAdapter().getReferenceInfo(req.params("reference"),
					req.session().attribute("user"));
			return HTTPResponseHelper.generateResponse(res, 200, rdi.toJSON(), null);
		});

		get(REF_URL, (req, res) -> {
			JSONArray refs = new JSONArray();
			Map<String, OpenWareDataItem> cRefs = DataService.getReferenceAdapter()
					.getCurrentReferences(req.session().attribute("user"));
			/*
			Set<String> sources = DataService.getItems(req.session().attribute("user")).stream().map(item -> {
				return item.getUser();
			}).collect(Collectors.toSet());
			
			for (String ref : cRefs.keySet()) {
				if (sources.contains(ref)) {
					JSONObject toPut = new JSONObject();
					toPut.put("item", cRefs.get(ref).toJSON());
					toPut.put("reference", ref);
					refs.put(toPut);
				}
			
			}
			*/
			for (String ref : cRefs.keySet()) {
				JSONObject toPut = new JSONObject();
				toPut.put("item", cRefs.get(ref).toJSON());
				toPut.put("reference", ref);
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
			User user = (User) req.session().attribute("user");
			if (!user.canAccessWrite(SERVICE_ACCESS_ID, source)) {
				return HTTPResponseHelper.generateResponse(res, 405, null,
						"Missing access permission to set reference for " + source);
			}
			DataService.getReferenceAdapter().setReferenceGlobalReferenceForSource(source, ref);
			return HTTPResponseHelper.generateResponse(res, 200, "Reference set for " + source, null);
		});
	}

}

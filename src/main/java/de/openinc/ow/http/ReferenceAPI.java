package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.ReferenceDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;

public class ReferenceAPI implements OpenWareAPI {
	public static String SERVICE_ACCESS_ID = "SERVICE_REFERENCE";
	private static String REF_URL = "/references";

	@Override
	public void registerRoutes() {
		get(REF_URL + "/{reference}/{start}/{end}", ctx -> {
			Map<String, List<String>> params = ctx.queryParamMap();
			if (params.containsKey("sensor") && params.containsKey("source")) {
				try {
					ReferenceDataItem rdi = DataService.getReferenceAdapter().getReferencedData(
							ctx.pathParam("reference"), ctx.sessionAttribute("user"), params.get("source").get(0),
							params.get("sensor").get(0), ctx.pathParamAsClass("start", Long.class).get(),
							ctx.pathParamAsClass("end", Long.class).get());
					HTTPResponseHelper.ok(ctx, rdi.toJSON());
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("Reference Error", e);
					HTTPResponseHelper.internalError("Could not retrieve referenced data: " + e.getCause());
				}
			} else {
				try {
					ReferenceDataItem rdi = DataService.getReferenceAdapter().getAllReferencedData(
							ctx.pathParam("reference"), ctx.sessionAttribute("user"),
							ctx.pathParamAsClass("start", Long.class).get(),
							ctx.pathParamAsClass("end", Long.class).get());
					HTTPResponseHelper.ok(ctx, rdi.toJSON());
				} catch (Exception e) {
					HTTPResponseHelper.internalError("Could not retrieve referenced data: " + e.getCause());
				}
			}

		});

		get(REF_URL + "/{reference}", ctx -> {
			ReferenceDataItem rdi = DataService.getReferenceAdapter().getReferenceInfo(ctx.pathParam("reference"),
					ctx.sessionAttribute("user"));
			HTTPResponseHelper.ok(ctx, rdi.toJSON());
		});

		get(REF_URL, ctx -> {
			JSONArray refs = new JSONArray();
			Map<String, OpenWareDataItem> cRefs = DataService.getReferenceAdapter()
					.getCurrentReferences(ctx.sessionAttribute("user"));

			for (String ref : cRefs.keySet()) {
				JSONObject toPut = new JSONObject();
				toPut.put("item", cRefs.get(ref).toJSON());
				toPut.put("reference", ref);
				refs.put(toPut);
			}
			HTTPResponseHelper.ok(ctx, refs);

		});

		post(REF_URL + "/{refid}", ctx -> {
			JSONObject body = new JSONObject(ctx.body());
			String ref = ctx.pathParam("refid");
			if (!body.has("source")) {
				HTTPResponseHelper.badRequest("Missing source parameter");
			}
			String source = body.getString("source");
			User user = (User) ctx.sessionAttribute("user");
			if (!user.canAccessWrite(SERVICE_ACCESS_ID, source)) {
				HTTPResponseHelper.forbidden("Missing access permission to set reference for " + source);
			}
			DataService.getReferenceAdapter().setReferenceGlobalReferenceForSource(source, ref);
			HTTPResponseHelper.ok(ctx, "Reference set for " + source);
		});
	}

}

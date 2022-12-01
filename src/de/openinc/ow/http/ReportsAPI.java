package de.openinc.ow.http;

import static io.javalin.apibuilder.ApiBuilder.post;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.api.ReportInterface;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.ReportsService;
import io.javalin.http.Context;
import jakarta.servlet.ServletOutputStream;

public class ReportsAPI implements OpenWareAPI {
	private ReportsService service;

	public ReportsAPI() {
		service = ReportsService.getInstance();
	}

	@Override
	public void registerRoutes() {
		post("/report/{type}", ctx -> {
			handle(ctx);
		});
	}

	public void handle(Context ctx) throws Exception {

		if (ctx.method().toString().toUpperCase().equals("POST")) {
			handlePost(ctx);
		}

	}

	private void handlePost(Context ctx) {
		User user = ctx.sessionAttribute("user");
		if (user == null) {
			OpenWareInstance.getInstance().logError("No User Provided for report");// TODO Auto-generated catch block
			HTTPResponseHelper.forbidden("Please login to access reporting service");
		}

		String reportType = ctx.pathParam("type");
		JSONObject params = new JSONObject(ctx.body());

		if (!params.has("params")) {
			HTTPResponseHelper.badRequest("Request body needs parameter object 'params' to configure report");
		}

		Class<ReportInterface> clazz = service.getReportType(reportType);

		try {

			ServletOutputStream out = ctx.res().getOutputStream();

			ReportInterface rep = service.generateReport(params.getJSONObject("params"), clazz, out, user);

			if (rep == null) {
				out.flush();
				OpenWareInstance.getInstance().logError(
						"Could not generate report for user: " + user + " with params \n" + params.toString(2));
				HTTPResponseHelper.badRequest(
						"Could not generate report for user: " + user + " with params \n" + params.toString(2));

			}
			ctx.contentType(rep.getContentType());
			ctx.header("Content-disposition", "attachment; filename=" + rep.getReportNameAndExtension());

			out.flush();
			out.close();

		} catch (IOException e) {
			OpenWareInstance.getInstance().logError("Could not read/write data to target", e);// TODO Auto-generated
			HTTPResponseHelper.internalError("Could not read/write data to target\n" + e.getMessage());

		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError("Parameter error", e);// TODO Auto-generated catch block
			HTTPResponseHelper.badRequest("Illegal Parameters provided " + e.getMessage());

		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Error while handling report", e);// TODO Auto-generated catch block
			HTTPResponseHelper.internalError("Error while handling report\n" + e.getMessage());
		}

	}

}

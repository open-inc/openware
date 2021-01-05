package de.openinc.ow.http;

import static spark.Spark.post;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.api.ReportInterface;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.ReportsService;
import spark.Request;
import spark.Response;

public class ReportsAPI implements OpenWareAPI {
	private ReportsService service;

	public ReportsAPI() {
		service = ReportsService.getInstance();
	}

	@Override
	public void registerRoutes() {
		post("/reports/:type", (req, res) -> {
			return handle(req, res);
		});
	}

	public Object handle(Request request, Response response) throws Exception {

		if (request.requestMethod().equals("POST")) {
			return handlePost(request, response);
		}
		return null;
	}

	private Object handlePost(Request request, Response response) {
		User user = request.session().attribute("user");
		if (user == null) {
			OpenWareInstance.getInstance().logError("No User Provided for report");// TODO Auto-generated catch block
			response.status(300);
			return "Please login to access reporting service";
		}

		String reportType = request.params("type");
		JSONObject params = new JSONObject(request.body());

		if (!params.has("params")) {
			response.status(500);
			return "Post Body needs Parameter Object 'params' to configure report";
		}

		Class<?> clazz = service.getReportType(reportType);

		try {

			HttpServletResponse raw = response.raw();

			OutputStream out = raw.getOutputStream();

			ReportInterface rep = service.generateReport(params.getJSONObject("params"), clazz, out, user);

			if (rep == null) {
				out.flush();
				out.close();
				response.status(500);

				OpenWareInstance.getInstance().logError(
						"Could not generate report for user: " +	user +
														" with params \n" +
														params.toString(2));
				return "Error while handling report";
			}
			raw.setContentType(rep.getContentType());
			raw.setHeader("Content-disposition", "attachment; filename=" + rep.getReportNameAndExtension());
			out.flush();
			out.close();

		} catch (IOException e) {
			OpenWareInstance.getInstance().logError("Could not read/write data to target", e);// TODO Auto-generated catch block
			response.status(500);
			return e.getMessage();

		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError("Parameter error", e);// TODO Auto-generated catch block
			response.status(500);
			return "Parameter error:\n" + e.getMessage();
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Error while handling report", e);// TODO Auto-generated catch block
			response.status(500);
			return e.getMessage();
		}
		return 200;
	}

}

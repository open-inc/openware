package de.openinc.ow.middleware.services;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import de.openinc.api.ReportInterface;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;

public class ReportsService {

	private Map<String, Class<ReportInterface>> registeredTypes;

	private static ReportsService me;

	public static ReportsService getInstance() {
		if (me == null) {
			me = new ReportsService();
		}
		return me;
	}

	private ReportsService() {
		this.registeredTypes = new HashMap<String, Class<ReportInterface>>();
	}

	public Class<ReportInterface> addReportType(String tag, Class<ReportInterface> ri) {
		Object o;
		try {
			o = ri.newInstance();
			return registeredTypes.put(tag, ri);

		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Could not register report", e);
			return null;
		}

	}

	public Class<ReportInterface> removeReportType(String tag) {
		return registeredTypes.remove(tag);
	}

	public Class<ReportInterface> getReportType(String tag) {
		return registeredTypes.get(tag);
	}

	public ReportInterface generateReport(JSONObject params, Class<ReportInterface> reportType, OutputStream out,
			User user) {
		return generateReport(params, reportType, out, user, null);
	}

	public ReportInterface generateReport(JSONObject params, Class<ReportInterface> reportType, OutputStream out,
			User user, List<OpenWareDataItem> preloadedData) {
		JSONObject parameter = params;
		ReportInterface clazz;

		try {
			ReportInterface o = reportType.newInstance();
			clazz = o;
			clazz.init(parameter, user);
			if (parameter.has("start")) {
				clazz.setStart(parameter.getLong("start"));
			}
			if (parameter.has("end")) {
				clazz.setEnd(parameter.getLong("end"));
			}
			if (parameter.has("reference")) {
				clazz.setReference(parameter.getString("reference"));
			}
			List<OpenWareDataItem> data = clazz.getData(parameter);
			if (Config.getBool("accessControl", true)) {
				Iterator<OpenWareDataItem> it = data.iterator();
				while (it.hasNext()) {
					OpenWareDataItem item = it.next();
					if (!user.canAccessRead(item.getSource(), item.getId())) {
						OpenWareInstance.getInstance().logError(user.getName()
								+ "tried to access data without permission:" + item.getSource() + ":" + item.getId());
						it.remove();
					}
				}

			}
			if (preloadedData == null) {
				preloadedData = new ArrayList<OpenWareDataItem>();
			}
			preloadedData.addAll(data);
		} catch (Exception e1) {
			OpenWareInstance.getInstance().logError("Error while handling report", e1);// TODO Auto-generated catch
																						// block

			return null;
		}
		try {
			clazz.generate(out, preloadedData);
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Report generation error:\n" + clazz.getClass().toString() + "\n",
					e);
			return null;
		}

		return clazz;
	}

}

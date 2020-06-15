package de.openinc.ow.middleware.services;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import de.openinc.api.ReportInterface;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;

public class ReportsService {

	private Map<String, Class<?>> registeredTypes;

	private static ReportsService me;

	public static ReportsService getInstance() {
		if (me == null) {
			me = new ReportsService();
		}
		return me;
	}

	private ReportsService() {
		this.registeredTypes = new HashMap<String, Class<?>>();
	}

	public Class<?> addReportType(String tag, Class<?> ri) {
		Object o;
		try {
			o = ri.newInstance();
			if (o instanceof ReportInterface) {
				return registeredTypes.put(tag, ri);
			} else {
				return null;
			}
		} catch (InstantiationException e) {

			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

	}

	public Class<?> removeReportType(String tag) {
		return registeredTypes.remove(tag);
	}

	public Class<?> getReportType(String tag) {
		return registeredTypes.get(tag);
	}

	public ReportInterface generateReport(JSONObject params, Class<?> reportType, OutputStream out, User user) {
		JSONObject parameter = params;
		ReportInterface clazz;

		try {
			Object o = reportType.newInstance();
			if (!(o instanceof ReportInterface))
				return null;
			clazz = (ReportInterface) o;
			clazz.init(parameter);
		} catch (Exception e1) {
			OpenWareInstance.getInstance().logError("Error while handling report", e1);// TODO Auto-generated catch block

			return null;
		}
		List<OpenWareDataItem> data = clazz.getData(parameter);
		if (Config.accessControl) {
			Iterator<OpenWareDataItem> it = data.iterator();
			while (it.hasNext()) {
				OpenWareDataItem item = it.next();
				if (!user.canAccessRead(item.getUser(), item.getId())) {
					OpenWareInstance.getInstance()
							.logError(user.getName() + "tried to access data without permission:" +
										item.getUser() +
										":" +
										item.getId());
					it.remove();
				}
			}

		}
		try {
			clazz.generate(out, data);
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Report generation error:\n" + clazz.getClass().toString() +
													"\n",
					e);
			return null;
		}

		return clazz;
	}

}

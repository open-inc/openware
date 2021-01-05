package de.openinc.api;

import java.io.OutputStream;
import java.util.List;

import org.json.JSONObject;

import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;

public abstract class ReportInterface {

	public abstract void init(JSONObject options, User user) throws Exception;

	public abstract String getTitle();

	public abstract void setTitle(String title);

	public abstract String getDescription();

	public abstract void setDescription(String description);

	public abstract ReportInterface generate(OutputStream out, List<OpenWareDataItem> data) throws Exception;

	public abstract List<OpenWareDataItem> getData(JSONObject opts);

	public abstract String getReportNameAndExtension();

	public abstract String getContentType();

	public abstract String getTag();

}
package de.openinc.api;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.helper.Config;

/**
 * 
 * <p>
 * Extend this class to add your custom TransformationOperation by implementing
 * two methods: </br>
 * </br>
 * 1)
 * {@code apply(User user, OpenWareDataItem previousStepData, JSONObject parameters)}</br>
 * will be called to get data and apply your transformation or apply the
 * transformation to the data {@code previousStepData} </br>
 * </br>
 * 2) {@code getResult(User user, OpenWareDataItem previousStepData, JSONObject
 * parameters} returning the result of your transformation as
 * {@link OpenWareDataItem}
 * </p>
 * 
 * @author Martin Stein
 * 
 * 
 * 
 */
public abstract class TransformationOperation {
	private Long start = null;
	private Long end = null;

	public abstract TransformationOperation apply(User user, OpenWareDataItem old, JSONObject params) throws Exception;

	public abstract OpenWareDataItem getResult();

	public void setStart(long startTS) {
		this.start = startTS;

	}

	public void setEnd(long endTS) {
		this.end = endTS;

	}

	public final OpenWareDataItem process(User user, OpenWareDataItem old, JSONObject params) throws Exception {
		if (start != null) {
			params.put("start", start.longValue());
		}
		if (end != null) {
			params.put("end", end.longValue());
		}
		this.apply(user, old, params);
		OpenWareDataItem item = getResult();
		if (item == null)
			return item;
		if (Config.accessControl) {
			if (user == null || !user.canAccessRead(item.getUser(), item.getId())) {
				throw new IllegalAccessError(
						"Not allowed to access data produced by Transformation:\n" + params.toString(2));
			}
		}

		return item;
	}
}

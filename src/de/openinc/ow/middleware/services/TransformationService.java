package de.openinc.ow.middleware.services;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.TransformationOperation;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;

public class TransformationService {
	private static TransformationService me;

	private HashMap<String, Class<TransformationOperation>> ops;

	private TransformationService() {
		me = this;
		ops = new HashMap<>();

	}

	/**
	 * @param op
	 *            Class of Operation to add
	 * @return previously associated operation class or null
	 */
	public Class registerOperation(Class op) {

		return ops.put(op.getCanonicalName(), op);
	}

	/**
	 * @param op
	 *            Operation to be removed
	 * @return Returns the class of the removed operation or null if not associated
	 */
	public Class<TransformationOperation> removeOperation(Class op) {
		return ops.remove(op.getCanonicalName());
	}

	/**
	 * Acces to the Transformation Service
	 * 
	 * @return Singleton of the Transfomation Service, which can be used to access
	 *         TransformationOps
	 */
	public static TransformationService getInstance() {
		if (me == null) {
			new TransformationService();
		}
		return me;
	}

	/**
	 * Get an TransformationOperation to perform the specific
	 * 
	 * @param id
	 *            The ID of the Operation that should be returned
	 * @return An Instance of the Transformation Operation, which should be freed
	 *         after usage to be collected by GC
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	public TransformationOperation getOperation(String id) throws InstantiationException, IllegalAccessException {
		Class<TransformationOperation> op = ops.get(id);
		if (op == null)
			return null;
		return op.newInstance();
	}

	/**
	 * Perform multiple transformation operation sequentially
	 * 
	 * @param user
	 *            An optional user that can be used to check Data Access rights
	 *            during each stage
	 * @param optionalData
	 *            Optional initial Data that will be passed into first stage
	 * @param options
	 *            JSON object including of the an array named "stages", which
	 *            describes which operation to perform, an number value "start" and
	 *            "end" which represent hold an unix timestamp in milliseconds
	 *            indicating the optional start/end of the period the operations
	 *            should be performed with.
	 * @return The result of the pipe as {@link OpenWareDataItem}
	 */
	public OpenWareDataItem pipeOperations(User user, OpenWareDataItem optionalData, JSONObject options)
			throws Exception {
		OpenWareDataItem tempItem = null;
		JSONArray stages = options.getJSONArray("stages");
		Long start = null;
		Long end = null;
		try {
			start = options.getLong("start");
			end = options.getLong("end");
		} catch (JSONException e) {
			start = null;
			end = null;
		}

		for (int i = 0; i < stages.length(); i++) {
			TransformationOperation op = TransformationService.getInstance()
					.getOperation(stages.getJSONObject(i).getString("action"));
			if (op == null) {
				throw new IllegalArgumentException("Unkown operation " + stages.getJSONObject(i).getString("action"));

			}
			if (start != null) {
				op.setStart(start);
				op.setEnd(end);
			}
			tempItem = op.process(user, tempItem, stages.getJSONObject(i).getJSONObject("params"));
			if (tempItem == null) {
				throw new IllegalStateException("Could not perform stage " + i +
												":\n" +
												stages.getJSONObject(i).getJSONObject("params"));
			}
			if (Config.accessControl && tempItem != null) {
				if (user == null || !user.canAccessRead(tempItem.getUser(), tempItem.getId()))
					throw new IllegalAccessError("Not allowed to access data produced by stage " + i +
													":\n" +
													stages.getJSONObject(i).getJSONObject("params"));
			}
			op = null;
		}
		return tempItem;

	}

}

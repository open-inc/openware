package de.openinc.ow.middleware.services;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.core.api.transformation.TransformationOperation;
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
	public Class registerOperation(String id, Class op) {

		return ops.put(id, op);
	}

	/**
	 * @param op
	 *            Operation to be removed
	 * @return Returns the class of the removed operation or null if not associated
	 */
	public Class<TransformationOperation> removeOperation(String id) {
		return ops.remove(id);
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
	 * @param stages
	 *            JSON description of the stages
	 * @return The result of the pipe as
	 *         {@link de.openinc.ow.core.model.data.OpenWareDataItem}
	 */
	public OpenWareDataItem pipeOperations(User user, OpenWareDataItem optionalData, JSONObject options)
			throws Exception {
		OpenWareDataItem tempItem = null;
		JSONArray stages = options.getJSONArray("stages");
		for (int i = 0; i < stages.length(); i++) {
			TransformationOperation op = TransformationService.getInstance()
					.getOperation(stages.getJSONObject(i).getString("action"));
			if (op == null) {
				throw new IllegalArgumentException("Unkown operation " + stages.getJSONObject(i).getString("action"));

			}
			tempItem = op.apply(tempItem, stages.getJSONObject(i).getJSONObject("params")).getResult();
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

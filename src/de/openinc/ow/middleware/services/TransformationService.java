package de.openinc.ow.middleware.services;

import java.util.HashMap;

import de.openinc.ow.core.api.transformation.TransformationOperation;

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

}

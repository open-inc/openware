package de.openinc.api;

import java.util.Map;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.ReferenceDataItem;
import de.openinc.model.user.User;

public interface ReferenceAdapter {

	void init();

	ReferenceDataItem getAllReferencedData(String ref, User user, long start, long end) throws Exception;

	ReferenceDataItem getReferencedData(String ref, User user, String sensor, String source, long start, long end)
			throws Exception;

	ReferenceDataItem getReferenceInfo(String ref, User user) throws Exception;

	boolean updateReference(OpenWareDataItem item) throws IllegalArgumentException;

	boolean setReferenceGlobalReferenceForSource(String ref, String source) throws Exception;

	String getReferenceForSource(String source);

	Map<String, OpenWareDataItem> getCurrentReferences(User user);

}
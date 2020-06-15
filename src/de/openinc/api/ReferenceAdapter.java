package de.openinc.api;

import java.util.Map;

import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.ReferenceDataItem;
import de.openinc.ow.core.model.user.User;

public interface ReferenceAdapter {

	void init();

	ReferenceDataItem getReferencedData(String ref, User user, long start, long end) throws Exception;

	ReferenceDataItem getReferenceInfo(String ref, User user) throws Exception;

	boolean setReferenceForSource(String source, String ref) throws IllegalArgumentException;

	boolean setReferenceForSource(OpenWareDataItem item) throws IllegalArgumentException;

	String getReferenceForSource(String source);

	Map<String, String> getCurrentReferences();

}
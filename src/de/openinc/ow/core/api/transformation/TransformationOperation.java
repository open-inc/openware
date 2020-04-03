package de.openinc.ow.core.api.transformation;

import org.json.JSONObject;

import de.openinc.ow.core.model.data.OpenWareDataItem;

public interface TransformationOperation {

	public TransformationOperation apply(OpenWareDataItem old, JSONObject params) throws Exception;

	public OpenWareDataItem getResult();
}

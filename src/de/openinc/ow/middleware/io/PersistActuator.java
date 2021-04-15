package de.openinc.ow.middleware.io;

import java.util.concurrent.Future;

import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.TransformationService;

public class PersistActuator extends ActuatorAdapter {

	@Override
	protected Future<Boolean> processAction(String target, String topic, String payload, User user, JSONObject options,
			OpenWareDataItem optionalData, Object optionalTemplateOptions) throws Exception {
		OpenWareInstance.getInstance().logTrace("Performing persist action\n" + options.toString(2));
		OpenWareDataItem toStore = optionalData.cloneItem();
		if (target.equals("transform")) {
			toStore = TransformationService.getInstance().pipeOperations(user, optionalData,
					options.getJSONObject("extra"));
			OpenWareInstance.getInstance().logTrace(
					"Performed tansformation. Result:\n" + toStore != null ? toStore.toString() : "Returned Null");
		}
		if (user.canAccessWrite(toStore.getUser(), toStore.getId())) {
			OpenWareInstance.getInstance().logTrace("Persisting data\n" + toStore);
			return DataService.onNewData(toStore);
		} else {
			return null;
		}

	}

	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return "persist";
	}

	@Override
	public void init(JSONObject options) throws Exception {
		// TODO Auto-generated method stub

	}

}

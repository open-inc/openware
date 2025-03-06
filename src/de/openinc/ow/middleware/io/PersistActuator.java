package de.openinc.ow.middleware.io;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.TransformationService;

public class PersistActuator extends ActuatorAdapter {

	@Override
	protected Object processAction(String target, String topic, String payload, User user,
			JSONObject options, List<OpenWareDataItem> optionalData, Object optionalTemplateOptions)
			throws Exception {

		try {
			JSONArray data = new JSONArray(payload);
			for (int i = 0; i < data.length(); i++) {
				JSONObject item = data.getJSONObject(i);
				OpenWareDataItem toStore = OpenWareDataItem.fromJSON(item);
				optionalData.add(toStore);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		CompletableFuture<Boolean>[] results = new CompletableFuture[optionalData.size()];
		int i = 0;
		for (OpenWareDataItem item : optionalData) {
			OpenWareDataItem toStore = item.cloneItem();
			if (target.equals("transform")) {
				toStore =
						TransformationService.getInstance().pipeOperations(user, toStore, options);
				OpenWareInstance.getInstance()
						.logTrace("Performed tansformation. Result:\n" + toStore != null
								? toStore.toString()
								: "NULL");
			}
			if (user.canAccessWrite(toStore.getSource(), toStore.getId())) {
				OpenWareInstance.getInstance().logTrace("Persisting data\n" + toStore);
				CompletableFuture<Boolean> res = DataService.onNewData(toStore).get();
				results[i++] = res;
			} else {
				results[i++] = CompletableFuture.completedFuture(false);
			}
		}
		try {
			CompletableFuture.allOf(results).get();
			for (CompletableFuture<Boolean> cf : results) {
				if (!cf.get()) {
					return false;
				}
			}
			return true;
		} catch (Exception e) {
			return false;
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

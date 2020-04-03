package de.openinc.ow.middleware.services;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.ActuatorAdapter;
import de.openinc.ow.core.api.DataHandler;
import de.openinc.ow.core.api.DataSubscriber;
import de.openinc.ow.core.api.PersistenceAdapter;
import de.openinc.ow.core.api.ReferenceAdapter;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareNumber;
import de.openinc.ow.core.model.data.OpenWareValue;
import de.openinc.ow.core.model.data.OpenWareValueDimension;
import de.openinc.ow.core.model.user.User;
import spark.QueryParamsMap;

public class DataService {

	private static PersistenceAdapter adapter = null;
	private static ArrayList<DataSubscriber> subscriptions = new ArrayList();
	private static ArrayList<DataHandler> handler;
	private static HashMap<String, ActuatorAdapter> actuators;
	private static HashMap<String, OpenWareDataItem> items;
	private static HashMap<String, OpenWareDataItem> itemConfigs;
	private static ReferenceAdapter reference;
	private static ExecutorService pool;
	public static final String CONFIG_STORE_TYPE = "sensorconfig";

	public static void init() {
		items = new HashMap<>();
		handler = new ArrayList<>();
		actuators = new HashMap<>();
		itemConfigs = new HashMap<>();
		pool = Executors.newFixedThreadPool(4);

	}

	public static PersistenceAdapter getCurrentPersistenceAdapter() {
		return adapter;
	}

	public static ReferenceAdapter getReferenceAdapter() {
		return reference;
	}

	public static void setReferenceAdapter(ReferenceAdapter ra) {
		reference = ra;
		reference.init();
	}

	public static DataHandler addHandler(DataHandler dh) {
		if (handler.add(dh)) {
			return dh;
		}
		return null;
	}

	public static boolean storeItemConfiguration(User user, String dataString) throws JSONException {
		ArrayList<OpenWareDataItem> res = new ArrayList<>();
		if (dataString.startsWith("[")) {
			JSONArray array = new JSONArray(dataString);
			for (int i = 0; i < array.length(); i++) {
				try {
					res.add(checkObject(user, array.getJSONObject(i)));
				} catch (SecurityException e) {
					OpenWareInstance.getInstance().logError("User not allowed to configure item", e);
					return false;
				} catch (JSONException e) {
					OpenWareInstance.getInstance().logError("Item configuration not valid", e);
					return false;
				}

			}
		} else {
			try {
				OpenWareDataItem item = checkObject(user, new JSONObject(dataString));

				if (item == null)
					return false;
				if (!user.canAccessWrite(item.getUser(), item.getId()))
					return false;

				res.add(item);
			} catch (JSONException e) {
				OpenWareInstance.getInstance().logError("Item configuration not valid", e);
				return false;
			}
		}

		for (OpenWareDataItem item : res) {
			itemConfigs.put(item.getMeta().getString("source_source") + Config.idSeperator
					+ item.getMeta().getString("id_source"), item);
			DataService.storeGenericData(CONFIG_STORE_TYPE,
					item.getMeta().getString("source_source") + Config.idSeperator
							+ item.getMeta().getString("id_source"),
					item.toString());
		}

		return true;
	}

	public static boolean deleteItemConfig(User user, String owner, String sensorid_source) {
		if (!user.canAccessWrite(owner, sensorid_source)) {
			return false;
		}
		DataService.removeGenericData(CONFIG_STORE_TYPE, owner + Config.idSeperator + sensorid_source);
		OpenWareDataItem deleted = itemConfigs.remove(owner + Config.idSeperator + sensorid_source);
		return deleted != null;
	}

	private static OpenWareDataItem checkObject(User user, JSONObject o) throws JSONException, SecurityException {
		OpenWareDataItem item = OpenWareDataItem.fromJSON(o.toString());
		boolean wellFormed = (item.getMeta().has("id_source") && item.getMeta().has("active")
				&& item.getMeta().has("source_source")
				&& item.getMeta().has("visible"));
		OpenWareDataItem original = DataService.getLiveSensorData(item.getMeta().getString("id_source"),
				item.getMeta().getString("source_source"));
		for (int i = 0; i < item.getValueTypes().size(); i++) {
			if (item.getValueTypes().get(i).getClass().toString()
					.equals(original.getValueTypes().get(i).getClass().toString())) {
				continue;
			} else {
				wellFormed = false;
				break;
			}
		}
		if (!wellFormed)
			throw new JSONException("Posted Configuration Object is missing parameters or valueTypes are incorrect");

		String owner = item.getUser();
		String id_source = item.getMeta().getString("id_source");

		if (!user.canAccessWrite(owner, id_source)) {
			throw new SecurityException("No Permission to configure item " + owner +
										Config.idSeperator +
										id_source);
		}
		return item;
	}

	private static void initItemConfiguration() {
		String[] temp = DataService.getGenericData(CONFIG_STORE_TYPE, null);
		for (String conf : temp) {
			JSONObject o = new JSONObject(conf);
			String source_source = o.getJSONObject("meta").optString("source_source");
			if (source_source.equals("")) {
				source_source = o.getString("user");
			}
			itemConfigs.put(
					source_source + Config.idSeperator
							+ o.getJSONObject("meta").getString("id_source"),
					OpenWareDataItem.fromJSON(o.toString()));
		}
	}

	public static HashMap<String, OpenWareDataItem> getItemConfiguration(User user) {
		List<OpenWareDataItem> items = DataService.getItems(user);
		HashMap<String, OpenWareDataItem> res = new HashMap<>();
		for (OpenWareDataItem item : items) {
			String id = item.getId();
			String source = item.getUser();
			if (item.getMeta().has("id_source")) {
				id = item.getMeta().getString("id_source");
				if (item.getMeta().has("source_source")) {
					source = item.getMeta().getString("source_source");
				}
				if (!isCurrentCustomizedItem(item))
					continue;
			}
			res.put(source + Config.idSeperator + id, item);
			String[] temp = DataService.getGenericData(CONFIG_STORE_TYPE, item.getUser() + Config.idSeperator + id);
			if (temp != null && temp.length > 0) {
				OpenWareDataItem custom = OpenWareDataItem.fromJSON(temp[0]);
				if (!custom.getMeta().has("source_source")) {
					custom.getMeta().put("source_source", custom.getUser());
				}
				res.put(custom.getMeta().getString("source_source") + Config.idSeperator
						+ custom.getMeta().getString("id_source"), custom);
			}
		}
		return res;
	}

	private static boolean isCurrentCustomizedItem(OpenWareDataItem toTest) {
		String sourceID = toTest.getMeta().getString("id_source");
		String sourceSource = toTest.getMeta().optString("source_source").equals("") ? toTest.getUser()
				: toTest.getMeta().optString("source_source");
		String currentID = itemConfigs.get(sourceSource + Config.idSeperator + sourceID).getId();
		return sourceID.equals(currentID);
	}

	public static DataHandler removeHandler(DataHandler dh) {
		if (handler.remove(dh)) {
			return dh;
		}
		return null;
	}

	public static ActuatorAdapter addActuator(ActuatorAdapter aa) {
		return actuators.put(aa.getID(), aa);
	}

	public static ActuatorAdapter remove(ActuatorAdapter aa) {
		return actuators.remove(aa.getID());
	}

	public static Set<String> getActuators() {
		return actuators.keySet();
	}

	public static void setPersistenceAdapter(PersistenceAdapter data) {
		data.init();
		adapter = data;
		for (OpenWareDataItem item : adapter.getItems()) {
			items.put(item.getUser() + item.getId(), item);
		}
		initItemConfiguration();
	}

	public static DataSubscriber addSubscription(DataSubscriber sub) {
		ArrayList<DataSubscriber> temp = new ArrayList<>();
		temp.addAll(subscriptions);
		try {
			temp.add(sub);
			subscriptions = temp;
		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
		return sub;
	}

	public static DataSubscriber removeSubscription(DataSubscriber sub) {
		subscriptions.remove(sub);
		return sub;
	}

	public static Future<Boolean> onNewData(String id, String data) {
		if (data != null && data.length() > 0) {
			return pool.submit(new DataProcessTask(id, data));
		}
		return ConcurrentUtils.constantFuture(false);
	}

	public static Future<Boolean> onNewData(List<OpenWareDataItem> item) {
		try {
			if (item != null && item.size() > 0) {
				return pool.submit(new DataProcessTask(item));
			}
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Exception while processing new Data \n", e);
		}
		return ConcurrentUtils.constantFuture(false);
	}

	protected static List<DataHandler> getHandler() {
		return handler;
	}

	protected static void notifySubscribers(OpenWareDataItem item) {
		for (DataSubscriber ds : subscriptions) {
			try {
				ds.receive(item);
			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Subscriber Error: " + e.getMessage(), e);
			}

		}
	}

	public static List<OpenWareDataItem> getItems(User user) {

		User cUser = user;
		if (cUser == null) {
			return new ArrayList<OpenWareDataItem>();
		}

		Iterator<OpenWareDataItem> it = items.values().iterator();
		ArrayList<OpenWareDataItem> items2return = new ArrayList<>();
		while (it.hasNext()) {
			OpenWareDataItem cItem = it.next();
			if (cUser.canAccessRead(cItem.getUser(), cItem.getId())) {
				items2return.add(cItem);
			}
		}
		Map<String, OpenWareDataItem> analytics = AnalyticsService.getInstance().getAnalyticSensors(user); // TEST
		if (analytics != null && analytics.size() > 0) {
			items2return.addAll(analytics.values());
		}
		return items2return;

	}

	public static List<OpenWareDataItem> getItems() {
		ArrayList<OpenWareDataItem> toReturn = new ArrayList<>();
		toReturn.addAll(items.values());
		return toReturn;
	}

	public static OpenWareDataItem getHistoricalSensorData(String sensorName, String source, long timestamp,
			long until) {
		return adapter.getHistoricalSensorData(sensorName, source, timestamp, until);

	}

	public static OpenWareDataItem getHistoricalSensorData(String sensorName, String source, long timestamp, long until,
			QueryParamsMap parameters) {
		OpenWareDataItem data = getHistoricalSensorData(sensorName, source, timestamp, until);
		ArrayList<OpenWareValue> toReturn = new ArrayList<>();

		String mode = parameters.value("mode");
		int maxAmount = parameters.get("maxValues").integerValue();
		int dim = parameters.get("dimension").integerValue();

		if (maxAmount >= data.value().size()) {
			return data;
		}

		int bucketSize = (int) Math.ceil((double) data.value().size() / (double) (maxAmount / 2));

		if (mode.toLowerCase().equals("minmax")) {
			int count = 0;
			double maxVal = Double.MIN_VALUE;
			double minVal = Double.MAX_VALUE;
			OpenWareValue max = null;
			OpenWareValue min = null;
			for (OpenWareValue val : data.value()) {
				if (count == bucketSize) {
					if (max == null || min == null) {
						System.out.println(max);
						System.out.println(min);
					} else {
						if (max.getDate() > min.getDate()) {
							toReturn.add(min);
							toReturn.add(max);
						} else {
							toReturn.add(max);
							toReturn.add(min);
						}
					}
					count = 0;
					maxVal = Double.MIN_VALUE;
					minVal = Double.MAX_VALUE;
					max = null;
					min = null;
				}
				double current = (double) val.get(dim).value();
				if (current > maxVal) {
					max = val;
					maxVal = current;
				}
				if (current <= minVal) {
					min = val;
					minVal = current;
				}
				count++;

			}

			data.value(toReturn);
			return data;
		}

		return data;

	}

	protected static boolean processNewData(List<OpenWareDataItem> items) {
		boolean result = true;
		for (OpenWareDataItem item : items) {
			if (!processNewData(item)) {
				result = false;
			}

		}
		return result;
	}

	/**
	 * Method applies personal configuration (e.g. set custom names or applies
	 * transformation if value is scaled) to incoming, "to-be-stored" items
	 * 
	 * @param item
	 *            The item which should be stored
	 * @return
	 */
	private static OpenWareDataItem applyItemConfiguration(OpenWareDataItem item) {
		OpenWareDataItem conf = itemConfigs.get(item.getUser() + Config.idSeperator + item.getId());
		if (conf != null) {
			if (!(conf.getMeta().has("active") && conf.getMeta().getBoolean("active"))) {
				return null;
			}
			JSONArray vTypes = conf.getMeta().optJSONArray("valueBounds");
			if (vTypes != null) {
				for (int i = 0; i < vTypes.length(); i++) {
					JSONObject cVType = vTypes.optJSONObject(i);
					if (cVType == null)
						continue;
					if (cVType.has("max") && cVType.has("min")) {
						double max = cVType.getDouble("max");
						double min = cVType.getDouble("min");
						for (OpenWareValue val : item.value()) {
							try {
								OpenWareNumber num = (OpenWareNumber) val.get(i);
								double newVal = min + ((num.value() / 32768.0) * (max - min));
								val.set(i, num.createValueForDimension(newVal));
							} catch (ClassCastException e) {
								break;
							} catch (IllegalArgumentException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								break;
							}
						}
					}
				}
			}

		} else {
			return item;
		}
		conf.value(item.value());
		return conf;
	}

	/**
	 * Stores item using PersistenceAdapter and notifies
	 * {@link de.openinc.ow.core.api.DataSubscriber subscribers}
	 * 
	 * @see de.openinc.ow.core.api.DataSubscriber
	 * 
	 * @param item
	 *            The item to be stored
	 * @return true if Item was stored
	 */
	private static boolean processNewData(OpenWareDataItem item) {

		item = applyItemConfiguration(item);
		if (item == null) {
			OpenWareInstance.getInstance().logDebug("Item was not stored due to 'active' flag");
			return true;
		}
		boolean stored = false;
		OpenWareDataItem lastItem = DataService.getLiveSensorData(item.getId(), item.getUser());
		String lastHash = null;
		// Ensuring vTypeHash in existing Item,e.g. if stored Item was not Hashed before
		if (lastItem == null) {
			lastItem = item;
		}
		if (!lastItem.getMeta().has("vTypeHash")) {
			lastHash = getValueTypeHash(lastItem, false);
			lastItem.getMeta().put("vTypeHash", lastHash);
		} else {
			lastHash = lastItem.getMeta().getString("vTypeHash");
		}

		if (!item.getMeta().has("vTypeHash")) {
			item.getMeta().put("vTypeHash", getValueTypeHash(item, false));
		}
		boolean baseDataRevision = !lastHash.equals(item.getMeta().get("vTypeHash"));
		boolean referenceChange = false;
		//Check and/or Set the reference
		if (item.getReference() != null) {
			reference.setReferenceForSource(item);
			//Item has reference so compare if differs from lastItem's reference
			referenceChange = !item.getReference().equals(lastItem.getReference());
		} else {
			//Item had no reference and new potential reference is set
			String ref = reference.getReferenceForSource(item.getUser());
			item.setReference(ref);

			if (lastItem.getReference() == null) {
				//Check if lastItem had no reference, so check if newItem has one now
				referenceChange = item.getReference() != null;
			} else {
				//lastItem had a reference, so check if newItem still has the same
				referenceChange = lastItem.getReference().equals(item.getReference());
			}

		}

		if (referenceChange || baseDataRevision) {
			item.getMeta().put("BaseDataRevision", true);
		}
		DataService.setCurrentItem(item);

		try {

			if (Config.dbPersistValue && item.persist()) {
				DataService.storeData(item);
				stored = true;
			} else {
				OpenWareInstance.getInstance().logWarn("Item was not Stored due to configuration");
				if (Config.verbose) {
					OpenWareInstance.getInstance().logTrace("Item:" + item.toString());
				}
			}

		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Could not store new Date: " + e.getMessage(), e);
			return false;
		}

		try {
			DataService.notifySubscribers(item);
		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Exception in Subscriber", e);
		}
		//Acknowledge storing -> Either item was stored or was flagged to not be stored

		return stored || !item.persist() || !Config.dbPersistValue;

	}

	public static JSONArray getStats() {
		return adapter.getStats();
	}

	public static OpenWareDataItem getLiveSensorData(String sensorName, String user) {
		return items.get(user + sensorName);
	}

	public static boolean deleteDeviceData(String sensorName, String user, Long from, Long until) {
		return adapter.deleteDeviceData(sensorName, user, from, until);
	}

	protected static void storeData(OpenWareDataItem item) throws Exception {
		adapter.storeData(item);
	}

	protected static void setCurrentItem(OpenWareDataItem item) {
		synchronized (items) {
			items.put(item.getUser() + item.getId(), item);
		}

	}

	public static void storeGenericData(String type, String key, String value) {
		adapter.storeGenericData(type, key, value);
		OpenWareInstance.getInstance().logDebug("Stored Generic Data " + type +
												":" +
												key +
												"\n" +
												value);
	}

	public static void removeGenericData(String type, String key) {
		adapter.removeGenericData(type, key);
		OpenWareInstance.getInstance().logDebug("Removed Generic Data " + type +
												":" +
												key);
	}

	public static String[] getGenericData(String type, String key) {
		return adapter.getGenericData(type, key);
	}

	public static boolean sendData(String actuatorID, String address, String target, String payload) {
		ActuatorAdapter aa = actuators.get(actuatorID);
		if (aa != null) {
			return aa.send(address, target, payload);
		}
		return false;
	}

	public static String getValueTypeHash(OpenWareDataItem item, boolean includeValueNames) {
		String res = "";
		for (OpenWareValueDimension dim : item.getValueTypes()) {
			res += dim.getUnit();
			res += dim.type();
			if (includeValueNames) {
				res += dim.getName();
			}
		}
		/*
		try {
			if (digest == null) {
				digest = MessageDigest.getInstance("SHA-1");
		
			} else {
				digest.reset();
			}
		
			digest.update(res.getBytes("UTF-8"));
		
		} catch (Exception e) {
			System.err.println("Error while creating VTypeHash");
			return null;
		}
		
		return new String(digest.digest());
		*/
		return res;
	}

}

class DataProcessTask implements Callable<Boolean> {

	private List<OpenWareDataItem> item;
	private String id;
	private String data;

	public DataProcessTask(List<OpenWareDataItem> item) {
		this.item = item;
	}

	public DataProcessTask(String id, String data) {
		this.id = id;
		this.data = data;
	}

	@Override
	public Boolean call() throws Exception {
		if (item != null) {
			return processItems(item);
		}
		if (data != null) {
			List<OpenWareDataItem> toProcess = null;
			for (DataHandler dh : DataService.getHandler()) {
				try {
					toProcess = dh.handleData(id, data);
					if (toProcess == null)
						continue;
				} catch (Exception e) {
					OpenWareInstance.getInstance().logError("Exception in Handler", e);
					continue;
				}
				return processItems(toProcess);
			}

		}
		return true;
	}

	private boolean processItems(List<OpenWareDataItem> items) {
		if (items == null) {
			return false;
		}
		return DataService.processNewData(items);
	}
}

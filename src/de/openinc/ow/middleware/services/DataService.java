package de.openinc.ow.middleware.services;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.api.DataHandler;
import de.openinc.api.DataSubscriber;
import de.openinc.api.PersistenceAdapter;
import de.openinc.api.ReferenceAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.transformation.FilterTransformer;
import spark.QueryParamsMap;

/**
 * The DataService provides the central interface all data related actions of
 * the middleware.
 * 
 * @author stein@openinc.de
 *
 */
/**
 * @author marti
 *
 */
/**
 * @author marti
 *
 */
/**
 * @author marti
 *
 */
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

	/**
	 * The init methods needs be called once before all services can be used. It
	 * initializes all caches and the ThreadPool for data processing
	 */
	public static void init() {
		items = new HashMap<>();
		handler = new ArrayList<>();
		actuators = new HashMap<>();
		itemConfigs = new HashMap<>();
		pool = Executors.newFixedThreadPool(4);

	}

	/**
	 * Can be used to access the current PersistenceAdpater
	 * 
	 * @return The current {@link de.openinc.ow.core.api.PersistenceAdapter}
	 */
	public static PersistenceAdapter getCurrentPersistenceAdapter() {
		return adapter;
	}

	/**
	 * Can be used to access the current ReferenceAdapter
	 * 
	 * @return The current {@link de.openinc.ow.core.api.ReferenceAdapter}
	 */
	public static ReferenceAdapter getReferenceAdapter() {
		return reference;
	}

	/**
	 * Can be used to set the ReferenceAdapter
	 * 
	 * @param ra
	 *            Reference to the ReferenceAdapter that should be set.
	 */
	public static void setReferenceAdapter(ReferenceAdapter ra) {
		reference = ra;
		reference.init();
	}

	/**
	 * Can be used to add a {@link de.openinc.ow.core.api.DataHandler}. If you
	 * register a DataHandler, it will be called by the DataService when new
	 * unstructured data arrives. The DataService will sequentially pass the
	 * information to the DataHandlers until one handler can parse the data and
	 * return a {@link de.openinc.model.data.OpenWareDataItem} Object
	 * 
	 * @param dh
	 * @return
	 */
	public static DataHandler addHandler(DataHandler dh) {
		if (handler.add(dh)) {
			return dh;
		}
		return null;
	}

	/**
	 * Can be used to apply configuration to OpenWareDataItems before they get
	 * stored. E.g. to change the name or even the ID of an Item
	 * 
	 * @param user
	 *            User used for authorization. Needs write Access
	 * @param dataString
	 *            JSONArray or JSONObject representing a OpenWareDataItem. This
	 *            template will be applied to the respective OpenWareDataItems
	 *            before they get persisted.
	 * @return True, if the configuration is valid and could be stored
	 * @throws JSONException
	 *             If invalid JSON was provided
	 */
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
				} catch (Exception e) {
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
			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Item configuration not valid", e);
				return false;
			}
		}

		for (OpenWareDataItem item : res) {
			try {
				String saved_id;
				OpenWareDataItem previous = itemConfigs
						.get(item.getMeta().getString("source_source") + Config.idSeperator
								+ item.getMeta().getString("id_source"));

				String id = null;
				if (previous != null) {
					id = previous.getMeta().getString("configuration_id");
				}
				saved_id = DataService.storeGenericData(CONFIG_STORE_TYPE,
						id,
						item.toJSON());

				item.getMeta().put("configuration_id", saved_id);
				itemConfigs.put(item.getMeta().getString("source_source") + Config.idSeperator
						+ item.getMeta().getString("id_source"), item);
				//Get current Item to upadte the configuration immediately
				refreshConfigurationOfItem(item);
			} catch (JSONException e) {
				e.printStackTrace();
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}

		}

		return true;
	}

	private static void refreshConfigurationOfItem(OpenWareDataItem item) {
		OpenWareDataItem cItem = DataService.getLiveSensorData(item.getId(), item.getUser());
		if (cItem == null) {
			//No current item. ID or Source has been customized. Retrieve source item
			String source_source = item.getMeta().optString("source_source");
			String id_source = item.getMeta().optString("id_source");
			if (source_source.equals("") || id_source.equals("")) {
				OpenWareInstance.getInstance()
						.logWarn("Malformed configuration in item:\n" + item.toJSON().toString(2));
				return;
			}
			cItem = DataService.getLiveSensorData(id_source, source_source);
			cItem = applyItemConfiguration(cItem);
		}

		if (cItem == null) {
			//Nothing to update, as source Item also does not exist...
			return;
		}
		OpenWareDataItem clonedConf = item.cloneItem();
		clonedConf.value().clear();
		clonedConf.value(cItem.value());
		setCurrentItem(clonedConf);
	}

	/**
	 * @param user
	 *            User used to check authorization. Needs write Access
	 * @param source_source
	 *            The original source field of the OpenWareDataItem
	 *            (Pre-configuration)
	 * @param sensorid_source
	 *            The original id of the OpenWareDataItem (Pre-configuration)
	 * @return True, if configuration was deleted
	 * @throws Exception
	 * @throws JSONException
	 */
	public static boolean deleteItemConfig(User user, String source_source, String sensorid_source)
			throws JSONException, Exception {
		if (!user.canAccessWrite(source_source, sensorid_source)) {
			return false;
		}
		OpenWareDataItem deleted = itemConfigs.remove(source_source + Config.idSeperator + sensorid_source);
		DataService.removeGenericData(CONFIG_STORE_TYPE, deleted.getMeta().getString("configuration_id"));

		return deleted != null;
	}

	private static OpenWareDataItem checkObject(User user, JSONObject o) throws Exception {
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

		String owner = item.getMeta().optString("source_source");
		if (owner.equals("")) {
			owner = item.getUser();
		}
		String id_source = item.getMeta().getString("id_source");

		if (!user.canAccessWrite(owner, id_source)) {
			throw new SecurityException("No Permission to configure item " +	owner +
										Config.idSeperator +
										id_source);
		}
		return item;
	}

	private static void initItemConfiguration() {
		try {
			List<JSONObject> temp = DataService.getGenericData(CONFIG_STORE_TYPE, null);
			for (JSONObject o : temp) {
				String source_source = o.getJSONObject("meta").optString("source_source");
				if (source_source.equals("")) {
					source_source = o.getString("user");
				}
				OpenWareDataItem configItem = OpenWareDataItem.fromJSON(o.toString());
				configItem.getMeta().put("configuration_id", o.getString("_id"));
				itemConfigs.put(
						source_source + Config.idSeperator
								+ o.getJSONObject("meta").getString("id_source"),
						configItem);
				refreshConfigurationOfItem(configItem);
			}
		} catch (Exception e) {
			OpenWareInstance.getInstance().logWarn("Could not load Item Configurations");
		}

	}

	/**
	 * Retrieves a list of currently configured items as map
	 * 
	 * @param user
	 *            User used for ACL Check
	 * @return A map of configurations that the user can see.
	 */
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
			List<JSONObject> temp;
			try {
				temp = DataService.getGenericData(CONFIG_STORE_TYPE,
						item.getMeta().getString("configuration_id"));

				if (temp != null && temp.size() > 0) {
					OpenWareDataItem custom = OpenWareDataItem.fromJSON(temp.get(0));
					if (!custom.getMeta().has("source_source")) {
						custom.getMeta().put("source_source", custom.getUser());
					}
					res.put(custom.getMeta().getString("source_source") + Config.idSeperator
							+ custom.getMeta().getString("id_source"), custom);
				}
			} catch (JSONException e) {
				continue;
			} catch (Exception e) {
				continue;
			}
		}
		return res;
	}

	private static boolean isCurrentCustomizedItem(OpenWareDataItem toTest) {
		String sourceID = toTest.getMeta().getString("id_source");
		String sourceSource = toTest.getMeta().optString("source_source").equals("") ? toTest.getUser()
				: toTest.getMeta().optString("source_source");
		OpenWareDataItem currentConfiged = itemConfigs.get(sourceSource + Config.idSeperator + sourceID);
		return toTest.equals(currentConfiged);
	}

	/**
	 * Removes a DataHandler from the DataHandler list. DataService will not use it
	 * anymore while parsing new data.
	 * 
	 * @param dh
	 *            The DataHandler to be removed
	 * @return The data handler that was removed or null if it fails
	 */
	public static DataHandler removeHandler(DataHandler dh) {
		if (handler.remove(dh)) {
			return dh;
		}
		return null;
	}

	/**
	 * Adds an actuator
	 * 
	 * @param aa
	 *            The Actuator to add
	 * @return the previous ActuatorObject associated with the ID, or null if there
	 *         was no mapping for the ID
	 */
	public static ActuatorAdapter addActuator(ActuatorAdapter aa) {
		return actuators.put(aa.getType(), aa);
	}

	/**
	 * Retrieves an actuator
	 * 
	 * @param id
	 *            The id of the actuator
	 * @return the actuator associated with the {@code id}
	 */
	public static ActuatorAdapter getActuator(String id) {
		return actuators.get(id);
	}

	/**
	 * Removes an Actuator
	 * 
	 * @param aa
	 *            The Actuator to be removed
	 * @return The removed Actuator
	 */
	public static ActuatorAdapter remove(ActuatorAdapter aa) {
		return actuators.remove(aa.getType());
	}

	/**
	 * Retrieve a list of available Actuators by ID
	 * 
	 * @return A Set of currently available Actuators
	 */
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
		//MQTT-Seperator to AMQP seperator
		id = id.replace("/", ".");
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

	public static Future<Boolean> onNewData(OpenWareDataItem item) {
		try {
			if (item != null) {
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

	protected static void notifySubscribers(OpenWareDataItem old, OpenWareDataItem newitem) {
		for (DataSubscriber ds : subscriptions) {
			try {
				ds.receive(old, newitem);
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
	
	public static int updateData(OpenWareDataItem item) throws Exception {
		for(OpenWareValue val: item.value()) {
			OpenWareDataItem pastLiveVal = getLiveSensorData(item.getId(), item.getUser(), val.getDate(), 1);
			if(!pastLiveVal.equalsValueTypes(item, false)) throw new IllegalArgumentException("The value types of the provided data differ from the existing data");
		}
		return adapter.updateData(item);
	}

	public static OpenWareDataItem getHistoricalSensorData(String sensorName, String source, long timestamp,
			long until) {

		return adapter.historicalData(sensorName, source, timestamp, until, null);

	}
	public static OpenWareDataItem getHistoricalSensorData(String sensorName, String source, long timestamp,
			long until, String ref) {

		return adapter.historicalData(sensorName, source, timestamp, until, ref);

	}

	private static List<OpenWareValue> getMinMax(OpenWareDataItem data, int maxAmount, int dim) {
		if(!data.getValueTypes().get(dim).type().toLowerCase().equals(OpenWareNumber.TYPE.toLowerCase())) return data.value();
		int bucketSize = (int) (data.value().size() / (double) (maxAmount / 2));
		ArrayList<OpenWareValue> resNew = new ArrayList<OpenWareValue>();
		int i=0;
		Map<Integer, List<OpenWareValue>> groups = new HashMap<Integer, List<OpenWareValue>>();
		while(i<data.value().size()) {
			groups.put(i, data.value().subList(i, Math.min((i+bucketSize), data.value().size())));
			i+=bucketSize;
		}
	
		groups.values().parallelStream().forEach(new Consumer<List<OpenWareValue>>() {
			
			@Override
			public void accept(List<OpenWareValue> list) {
				try {
					OpenWareValue min =list.parallelStream().reduce(new BinaryOperator<OpenWareValue>() {
						@Override
						public OpenWareValue apply(OpenWareValue t, OpenWareValue u) {
							return (double)t.get(dim).value()<(double)u.get(dim).value()?t:u;
						}
					}).get(); 
					
					
					
					OpenWareValue max = list.parallelStream().reduce(new BinaryOperator<OpenWareValue>() {
						@Override
						public OpenWareValue apply(OpenWareValue t, OpenWareValue u) {
							return (double)t.get(dim).value()>(double)u.get(dim).value()?t:u;
						}
					}).get();
					synchronized (resNew) {
						resNew.add(max);
						resNew.add(min);
					}
				}catch(NoSuchElementException e) {
					OpenWareInstance.getInstance().logError("No Result for Min/Max in Bucket", e);
				}
				
			}

		});
		return resNew;
		
		
		
		
		
	}
	
	private static List<OpenWareValue> getMinMaxOld(OpenWareDataItem data, int maxAmount, int dim ) {
		if(!data.getValueTypes().get(dim).type().toLowerCase().equals(OpenWareNumber.TYPE.toLowerCase())) return data.value();
		ArrayList<OpenWareValue> toReturn = new ArrayList<>();
		int count = 0;
		double maxVal = Double.MIN_VALUE;
		double minVal = Double.MAX_VALUE;
		int bucketSize = (int) Math.ceil((double) data.value().size() / (double) (maxAmount / 2));
		
		
		OpenWareValue max = null;
		OpenWareValue min = null;
		for (OpenWareValue val : data.value()) {
			if (count == bucketSize) {
				if (max == null || min == null) {
					//						System.out.println(max);
					//						System.out.println(min);
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
		return toReturn;
	
		
	}
	
	private static OpenWareDataItem filter(QueryParamsMap params, OpenWareDataItem item) throws Exception {
		FilterTransformer ft = new FilterTransformer();
		JSONObject filterOpts = new JSONObject();
		filterOpts.put("filterExpression", params.value("filter"));
		return ft.apply(null, item, filterOpts).getResult();
		
	}
	public static OpenWareDataItem getHistoricalSensorData(String sensorName, String source, long timestamp, long until,
			QueryParamsMap parameters) {
		
		String ref = parameters.value("reference");
		OpenWareDataItem data;
		if(ref!=null && !ref.equals("")) {
			data = getHistoricalSensorData(sensorName, source, timestamp, until, ref);
		}else {
			data = getHistoricalSensorData(sensorName, source, timestamp, until);	
		}
		if(parameters.hasKey("filter")) {
			try {
				data = filter(parameters, data);
				System.gc();
			} catch (Exception e) {
				OpenWareInstance.getInstance().logWarn("Error while filtering data");
			}
		}
		
		if(!parameters.hasKey("mode")) {
			return data;
		}
		
		String mode = parameters.value("mode");
		int maxAmount = parameters.get("maxValues").integerValue();
		int dim = parameters.get("dimension").integerValue();

		if (maxAmount >= data.value().size()) {
			return data;
		}
		if (!data.getValueTypes().get(dim).type().equals(OpenWareNumber.TYPE)) {
			return data;
		}
		if (mode.toLowerCase().equals("minmax")) {
			data.value(getMinMax(data, maxAmount, dim));
			return data;
		}
		if (mode.toLowerCase().equals("minmax2")) {
			data.value(getMinMaxOld(data, maxAmount, dim));
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
		if (item == null)
			return null;
		OpenWareDataItem conf = itemConfigs.get(item.getUser() + Config.idSeperator + item.getId());
		if (conf != null) {
			if (!(conf.getMeta().has("active") && conf.getMeta().getBoolean("active"))) {
				return null;
			}
			if (conf.getMeta().optBoolean("onChange")) {
				if (conf.equalsLastValue(item, (60000l * 60l))) {
					OpenWareInstance.getInstance()
							.logDebug("Item was not stored due to 'onChange' flag and same value");
					return null;
				}
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
							} catch (Exception e) {
								OpenWareInstance.getInstance().logError("SensorConfigurationDataservice", e);
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
	public static boolean processNewData(OpenWareDataItem item) {

		item = applyItemConfiguration(item);
		if (item == null) {
			OpenWareInstance.getInstance().logDebug("Item was not stored after applying configuration");
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
			reference.updateReference(item);
			//Item has reference so compare if differs from lastItem's reference
			referenceChange = !item.getReference().equals(lastItem.getReference());
		} else {
			//Item had no reference and new potential reference is set
			String ref = reference.getReferenceForSource(item.getUser());
			item.setReference(ref);
			reference.updateReference(item);
			if (lastItem.getReference() == null) {
				//Check if lastItem had no reference, so check if newItem has one now
				referenceChange = item.getReference() != null;
			} else {
				//lastItem had a reference, so check if newItem still has the same
				referenceChange = lastItem.getReference().equals(item.getReference());
			}

		}

		
			item.getMeta().put("BaseDataRevision", baseDataRevision);
			item.getMeta().put("referenceChange", referenceChange);
		
		DataService.setCurrentItem(item);

		try {

			if (Config.dbPersistValue && item.persist()) {
				DataService.storeData(item);
				stored = true;
			} else {
				OpenWareInstance.getInstance().logWarn("Item was not Stored due to configuration: " +	item.getUser() +
														Config.idSeperator +
														item.getId());
				if (Config.verbose) {
					OpenWareInstance.getInstance().logTrace("Item:" + item.toString());
				}
			}

		} catch (Exception e) {
			OpenWareInstance.getInstance().logError("Could not store new Date: " + e.getMessage(), e);
			return false;
		}
		pool.submit(new SubscriberRunnable(lastItem, item));
		//Acknowledge storing -> Either item was stored or was flagged to not be stored

		return stored || !item.persist() || !Config.dbPersistValue;

	}

	public static JSONArray getStats() {
		return adapter.getStats();
	}

	public static OpenWareDataItem getLiveSensorData(String sensorID, String source) {
		return items.get(source + sensorID);
	}
	public static OpenWareDataItem getLiveSensorData(String sensorID, String source, String reference) {
		return getLiveSensorData(sensorID, source, System.currentTimeMillis(), 1, reference);
	}
	
	public static OpenWareDataItem getLiveSensorData(String sensorID,String source, long at, int elements) {
		return getLiveSensorData(sensorID, source, at, elements, null);
	}
	public static OpenWareDataItem getLiveSensorData(String sensorID,String source, long at, int elements, String reference) {
		return adapter.liveData(sensorID, source, at, elements, reference);
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

	public static String storeGenericData(String type, String key, JSONObject value) throws Exception {
		String res = adapter.storeGenericData(type, key, value);
		if (res != null) {
			OpenWareInstance.getInstance().logDebug("Stored Generic Data " +	type +
													":" +
													res +
													"\n" +
													value);

		}
		return res;
	}

	public static void removeGenericData(String type, String key) throws Exception {
		adapter.removeGenericData(type, key);
		OpenWareInstance.getInstance().logDebug("Removed Generic Data " +	type +
												":" +
												key);
	}

	public static List<JSONObject> getGenericData(String type, String key) throws Exception {
		return adapter.getGenericData(type, key);
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
	private OpenWareDataItem singleItem;
	private String id;
	private String data;

	public DataProcessTask(List<OpenWareDataItem> item) {
		this.item = item;
	}

	public DataProcessTask(OpenWareDataItem sItem) {
		this.singleItem = sItem;
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
		if (singleItem != null) {
			return processItem(singleItem);
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
		//([a-zA-Z\d]+\.)+: ID und source prüfen
		if (items == null) {
			return false;
		}
		return DataService.processNewData(items);
	}

	private boolean processItem(OpenWareDataItem item) {
		//([a-zA-Z\d]+\.)+: ID und source prüfen
		if (item == null) {
			return false;
		}
		return DataService.processNewData(item);
	}
}

class SubscriberRunnable implements Runnable {
	OpenWareDataItem item;
	OpenWareDataItem old;

	public SubscriberRunnable(OpenWareDataItem old, OpenWareDataItem item) {
		this.item = item;
		this.old = old;
	}

	@Override
	public void run() {
		DataService.notifySubscribers(old, item);
		item = null;
		old = null;
	}

}

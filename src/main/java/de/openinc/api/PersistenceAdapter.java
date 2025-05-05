/**
 * Interface for a Persistence Adapter that handles storage and retrieval of OpenWare data.
 */
package de.openinc.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;

/**
 * This interface defines methods for initializing, closing, and interacting
 * with a persistence storage system.
 */
public interface PersistenceAdapter {

	/**
	 * Initializes the PersistenceAdapter.
	 *
	 * @throws Exception if initialization fails.
	 */
	void init() throws Exception;

	/**
	 * Closes the PersistenceAdapter, releasing any resources.
	 */
	void close();

	/**
	 * Retrieves statistics about the stored data.
	 *
	 * @return a JSONArray containing statistics.
	 */
	JSONArray getStats();

	/**
	 * Retrieves a list of OpenWareDataItems.
	 *
	 * @return a list of OpenWareDataItems.
	 */
	List<OpenWareDataItem> getItems();

	/**
	 * Retrieves live data for a specified sensor.
	 *
	 * @param id           the id of the sensor.
	 * @param source       the data source.
	 * @param at           the timestamp for the data.
	 * @param lastElements the number of last elements to retrieve.
	 * @param reference    a reference identifier that was used when storing data.
	 * @return an OpenWareDataItem representing live data.
	 */
	OpenWareDataItem liveData(String id, String source, long at, int lastElements, String reference);

	/**
	 * Retrieves historical data for a specified sensor within a time range.
	 *
	 * @param id        the id of the sensor.
	 * @param source    the data source.
	 * @param timestamp the start timestamp for the data.
	 * @param until     the end timestamp for the data.
	 * @param reference a reference identifier that was used when storing data.
	 * @param optionals {@link RetrievalOptions} retrieval options.
	 * @return an OpenWareDataItem representing historical data.
	 */
	OpenWareDataItem historicalData(String id, String source, Long timestamp, Long until, String reference,
			RetrievalOptions optionals);

	/**
	 * Retrieves the count of data entries for a specified sensor within a time
	 * range.
	 * 
	 * @param id        the id of the sensor whose data count is to be retrieved.
	 * @param source    the data source from which to retrieve the count.
	 * @param timestamp the start timestamp for the time range.
	 * @param until     the end timestamp for the time range.
	 * @param reference a reference identifier that was used when storing the data.
	 * @param optionals {@link RetrievalOptions} retrieval options that can specify
	 *                  additional parameters for data retrieval.
	 * @return an OpenWareDataItem representing the count of data entries in the
	 *         specified range.
	 */
	OpenWareDataItem countData(String id, String source, Long timestamp, Long until, String reference,
			RetrievalOptions optionals);

	/**
	 * Deletes device data within a specified time range.
	 *
	 * @param sensorid  the sensor identifier.
	 * @param source    the data source.
	 * @param from      the start timestamp for deletion.
	 * @param until     the end timestamp for deletion.
	 * @param reference a reference identifier that was used when storing data.
	 * @return true if deletion is successful, false otherwise.
	 * @throws Exception if deletion fails.
	 */
	boolean deleteDeviceData(String sensorid, String source, long from, long until, String reference) throws Exception;

	/**
	 * Stores OpenWareDataItem asynchronously.
	 *
	 * @param item the OpenWareDataItem to store.
	 * @return a CompletableFuture indicating the success of the operation.
	 * @throws Exception if storage fails.
	 */
	CompletableFuture<Boolean> storeData(OpenWareDataItem item) throws Exception;

	/**
	 * Updates OpenWareDataItem.
	 *
	 * @param item the OpenWareDataItem to update.
	 * @return the number of items updated.
	 * @throws Exception if update fails.
	 */
	int updateData(OpenWareDataItem item) throws Exception;

	/**
	 * Stores generic data.
	 *
	 * @param type        the type of data.
	 * @param optionalKey an optional key.
	 * @param value       the data value as a JSONObject.
	 * @return a unique identifier for the stored data.
	 * @throws Exception if storage fails.
	 */
	String storeGenericData(String type, String optionalKey, JSONObject value) throws Exception;

	/**
	 * Removes generic data.
	 *
	 * @param user the user identifier.
	 * @param key  the data key to remove.
	 * @throws Exception if removal fails.
	 */
	void removeGenericData(String user, String key) throws Exception;

	/**
	 * Retrieves generic data.
	 *
	 * @param type the type of data.
	 * @param key  the data key to retrieve.
	 * @return a list of JSONObjects representing the generic data.
	 * @throws Exception if retrieval fails.
	 */
	List<JSONObject> getGenericData(String type, String key) throws Exception;

	/**
	 * Schedules deletes for a specified source and id with a given time-to-live.
	 *
	 * @param source        the data source.
	 * @param id            the identifier for the data to be deleted.
	 * @param secondsToLive the time-to-live for the scheduled delete.
	 * @throws Exception if scheduling fails.
	 */
	void scheduleDeletes(String source, String id, long secondsToLive) throws Exception;

	/**
	 * Unschedules deletes for a specified source and id.
	 *
	 * @param source the data source.
	 * @param id     the identifier for the data.
	 * @throws Exception if unscheduling fails.
	 */
	void unScheduleDeletes(String source, String id) throws Exception;

	/**
	 * Retrieves a list of scheduled deletes for a specified source and id.
	 *
	 * @param source the data source.
	 * @param id     the identifier for the data.
	 * @return a list of JSONObjects representing the scheduled deletes.
	 * @throws Exception if retrieval fails.
	 */
	List<JSONObject> getScheduledDeletes(String source, String id) throws Exception;
}

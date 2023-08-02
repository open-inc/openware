package de.openinc.ow.helper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import io.github.cdimascio.dotenv.Dotenv;

public class Config {
	/*
	 * // Logging public static String logLevel; public static String logLevelAPIs;
	 * public static String timezone; public static String language; public static
	 * Boolean verbose;
	 * 
	 * // Spark config public static boolean enableWebserver; // Location from which
	 * static files will be served public static String sparkFileDir; // Location
	 * from which static files will be served public static String sparkPort; //
	 * Port on which spark will run (int) public static String sparkSSL; // Activate
	 * ssl (Boolean) public static String keystoreFilePath; // Keystore location,
	 * only needed when ssl is activated public static String keystorePassword; //
	 * Keystore password, only needed when ssl is activated public static String
	 * publicServer; // Set server to public, will turn on media APIs for Bot
	 * interaction etc. // (Boolean) public static Boolean useUsernames; // Activate
	 * API authentification against baasbox, work in progress (Boolean) public
	 * static String standardUser; // Name of standard user when server runs w/o
	 * authentication public static String allowDeleteData; // Sets if the delete
	 * data api is active (Boolean) public static Boolean accessControl; // Sets if
	 * whitelist will be used to filter Rest API calls. public static String
	 * whiteList; // Comma separated list of IP addresses that are allowed to access
	 * the Rest API.
	 * 
	 * // Database config public static String dbPath; // Location of the MongoDB
	 * public static String dbFindBatchSize; // Location of the MongoDB public
	 * static String dbPort; // Port of the MongoDB (int) public static String
	 * dbUser; // Username of MongoDB, needed public static String dbPass; //
	 * Password of MongoDB, needed public static String dbName; // Name of database
	 * to be used public static String dbSensorPrefix; // Prefix for sensor
	 * collections public static String dbContainerPrefix; // Prefix for contaienr
	 * collections public static String dbArchivePrefix; // Name of collection
	 * containing archived data public static String idSeperator; public static long
	 * baseTimeInterval; public static String dbGenericPrefix; public static boolean
	 * dbPersistValue; public static String referenceDBName; public static int
	 * maxConnectionsPerHost; public static int connectionQueueSizeMultiplier;
	 * public static String dbConnectionString; public static int
	 * aggregationIntervalInMinutes; public static boolean
	 * updateExistingAggregations; //Parse Generic Data public static String
	 * parseGenericClasses;
	 * 
	 * // Data Mapping public static String mappingsCollection; // Name of
	 * collection containing archived data
	 * 
	 * // RabbitMQ config public static String rmqPath; // Location of rabbitMQ
	 * middleware public static String rmqQueue; // Name of queue to be used for
	 * receiving messages public static Boolean rmqQueueAutoDelete; // Auto delete
	 * queue when server stops. Set to true for test systems. public static String
	 * rmqExchange; // Name of exchange to listen on public static String rmqTopic;
	 * // Name of topic to filter for public static String rmqUser; // Username of
	 * rabbitMQ, needed public static String rmqPass; // Password of rabbitMQ,
	 * needed public static String rmqPort; // Port of rabbitMW server (int) public
	 * static String validateData; // Activate data validation of incoming RMQ
	 * messages (Boolean) public static String requeueUnvalidated; // Don't store
	 * data that fails validation (Boolean) public static boolean publishParsedData;
	 * public static String rmqvHost;
	 * 
	 * 
	 * // MQTT public static String mqttAdresse; public static int mqttPort; public
	 * static String mqttTopic;
	 * 
	 * public static String analyticSensors;
	 * 
	 * public static String aggregationServiceUrl; // URL of od-aggregation-service
	 * (optional) public static String aggregationServicePort; // Port of
	 * od-aggregation-service (optional) public static String analyticPrefix; public
	 * static long analyticRefreshRate; public static String
	 * analyticOperationCollection;
	 * 
	 * // Mail public static String outboundMailServer; public static int
	 * outboundMailServerPort; public static String mailserverUser; public static
	 * String mailserverPassword;
	 * 
	 * //Webhook public static String webhookClass;
	 * 
	 * // Analytics
	 * 
	 * public static HashMap<String, JSONObject> analyticOperations;
	 * 
	 * // OPCUA public static boolean opcuaEnabled; public static int opcuaPort;
	 * 
	 * // Ticket Server public static boolean ticketEnabled; public static String
	 * ticketHost; public static int ticketPort; public static String ticketUser;
	 * public static String ticketType; public static String ticketWorkerGroup;
	 * public static String ticketAccessToken;
	 * 
	 * //Actuator public static String templatingRegexSelector; public static String
	 * templatingSectionRegexSelector;
	 * 
	 * // Chart Exporter public static String chartExporterURL;
	 * 
	 * //Report public static String reportUrlRoutePrefix;
	 * 
	 * //Scheduler public static int numberOfJobThreads;
	 */
	//
	public static Dotenv env;
	public static HashMap<String, JSONObject> idMappings;

	public static void init() {
		env = Dotenv.load();

		// analyticOperations = new HashMap<>();
		idMappings = new HashMap<>();

		// properties.load(new FileInputStream("spark.properties"));
	}

	public static JSONObject mapId(String external) {
		return idMappings.get(external);
	}

	public static String get(String name, String defaultVal) {
		return env.get(env.get("OW_ENV_PREFIX", "OW_") + name.toUpperCase(), defaultVal);
	}

	public static boolean getBool(String name, boolean defaultVal) {
		String toReturn = env.get(env.get("OW_ENV_PREFIX", "OW_") + name.toUpperCase());
		if (toReturn != null) {
			return Boolean.valueOf(toReturn);
		}
		return defaultVal;
	}

	public static int getInt(String name, int defaultVal) {
		String toReturn = env.get(env.get("OW_ENV_PREFIX", "OW_") + name.toUpperCase());
		if (toReturn != null) {
			return Integer.valueOf(toReturn);
		}
		return defaultVal;
	}

	public static double getDouble(String name, double defaultVal) {
		String toReturn = env.get(env.get("OW_ENV_PREFIX", "OW_") + name.toUpperCase());
		if (toReturn != null) {
			return Double.valueOf(toReturn);
		}
		return defaultVal;
	}

	public static long getLong(String name, long defaultVal) {
		String toReturn = env.get(env.get("OW_ENV_PREFIX", "OW_") + name.toUpperCase());
		if (toReturn != null) {
			return Long.valueOf(toReturn);
		}
		return defaultVal;
	}

	public static JSONObject readConfig(String configName) {
		try {
			String envConf = env.get(env.get("OW_ENV_PREFIX", "OW_") + configName.replace(".", "_").toUpperCase());
			if (envConf != null && !envConf.equals("")) {
				JSONObject test = new JSONObject(envConf);
				return test;
			}
		} catch (Exception e) {
			OpenWareInstance.getInstance().logWarn("Invalid ENV Variable set for " + configName);
		}

		try {
			String path = "conf" + File.separator + configName + ".json";
			JSONObject options = new JSONObject(new String(Files.readAllBytes(Paths.get(path))));
			return options;
		} catch (Exception e) {
			OpenWareInstance.getInstance().logWarn("No options provided for " + configName);
			return new JSONObject();
		}
	}
}

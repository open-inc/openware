package de.openinc.ow.core.helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;

public class Config {

	// Logging
	public static String logLevel;
	public static String logLevelAPIs;
	public static String timezone;
	public static String language;
	public static Boolean verbose;

	// Spark config
	public static boolean enableWebserver; // Location from which static files will be served
	public static String sparkFileDir; // Location from which static files will be served
	public static String sparkPort; // Port on which spark will run (int)
	public static String sparkSSL; // Activate ssl (Boolean)
	public static String keystoreFilePath; // Keystore location, only needed when ssl is activated
	public static String keystorePassword; // Keystore password, only needed when ssl is activated
	public static String publicServer; // Set server to public, will turn on media APIs for Bot interaction etc.
										// (Boolean)
	public static Boolean useUsernames; // Activate API authentification against baasbox, work in progress (Boolean)
	public static String standardUser; // Name of standard user when server runs w/o authentication
	public static String allowDeleteData; // Sets if the delete data api is active (Boolean)
	public static Boolean accessControl; // Sets if whitelist will be used to filter Rest API calls.
	public static String whiteList; // Comma separated list of IP addresses that are allowed to access the Rest API.

	// Database config
	public static String dbPath; // Location of the MongoDB
	public static String dbPort; // Port of the MongoDB (int)
	public static String dbUser; // Username of MongoDB, needed
	public static String dbPass; // Password of MongoDB, needed
	public static String dbName; // Name of database to be used
	public static String dbSensorPrefix; // Prefix for sensor collections
	public static String dbContainerPrefix; // Prefix for contaienr collections
	public static String dbArchivePrefix; // Name of collection containing archived data
	public static String idSeperator;
	public static long baseTimeInterval;
	public static String dbGenericPrefix;
	public static boolean dbPersistValue;
	public static String referenceDBName;
	public static int maxConnectionsPerHost;
	public static int connectionQueueSizeMultiplier;

	//Parse Generic Data
	public static String parseGenericClasses;

	// Data Mapping
	public static String mappingsCollection; // Name of collection containing archived data

	// RabbitMQ config
	public static String rmqPath; // Location of rabbitMQ middleware
	public static String rmqQueue; // Name of queue to be used for receiving messages
	public static Boolean rmqQueueAutoDelete; // Auto delete queue when server stops. Set to true for test systems.
	public static String rmqExchange; // Name of exchange to listen on
	public static String rmqTopic; // Name of topic to filter for
	public static String rmqUser; // Username of rabbitMQ, needed
	public static String rmqPass; // Password of rabbitMQ, needed
	public static String rmqPort; // Port of rabbitMW server (int)
	public static String validateData; // Activate data validation of incoming RMQ messages (Boolean)
	public static String requeueUnvalidated; // Don't store data that fails validation (Boolean)
	public static boolean publishParsedData;
	public static String rmqvHost;

	// MQTT
	public static String mqttAdresse;
	public static int mqttPort;
	public static String mqttTopic;

	public static String analyticSensors;

	public static String aggregationServiceUrl; // URL of od-aggregation-service (optional)
	public static String aggregationServicePort; // Port of od-aggregation-service (optional)
	public static String analyticPrefix;
	public static long analyticRefreshRate;
	public static String analyticOperationCollection;

	// Mail
	public static String outboundMailServer;
	public static int outboundMailServerPort;
	public static String mailserverUser;
	public static String mailserverPassword;

	//Webhook
	public static String webhookClass;

	// Analytics
	public static HashMap<String, JSONObject> idMappings;
	public static HashMap<String, JSONObject> analyticOperations;

	// OPCUA
	public static boolean opcuaEnabled;
	public static int opcuaPort;

	// Ticket Server
	public static boolean ticketEnabled;
	public static String ticketHost;
	public static int ticketPort;
	public static String ticketUser;
	public static String ticketType;
	public static String ticketWorkerGroup;
	public static String ticketAccessToken;

	//Actuator
	public static String templatingRegexSelector;
	public static String templatingSectionRegexSelector;

	// Chart Exporter
	public static String chartExporterURL;

	//Report
	public static String reportUrlRoutePrefix;

	//Scheduler
	public static int numberOfJobThreads;

	//

	public static void init() {

		analyticOperations = new HashMap<>();
		idMappings = new HashMap<>();
		Properties properties = new Properties();

		try {
			OpenWareInstance.getInstance().logError("--------------------------------------------------------------");
			OpenWareInstance.getInstance().logError("---------------------Restart Backend--------------------------");
			OpenWareInstance.getInstance().logError("--------------------------------------------------------------");

			OpenWareInstance.getInstance().logInfo("Reading config file spark.properties");
			properties.load(new FileInputStream("spark.properties"));
		} catch (IOException e) {
			OpenWareInstance.getInstance().logError("Config file not found. " + e.getMessage());
			OpenWareInstance.getInstance()
					.logError("You should provide a spark.properties file to define the server settings.");
		}

		chartExporterURL = properties.getProperty("chartExporterURL", "localhost");
		timezone = properties.getProperty("timezone", "Europe/Berlin");
		language = properties.getProperty("language", "de-DE");

		reportUrlRoutePrefix = properties.getProperty("reportUrlRoutePrefix", "/report");
		numberOfJobThreads = Integer.parseInt(properties.getProperty("numberOfJobThreads", "1"));
		parseGenericClasses = properties.getProperty("parseGenericClasses", "");
		enableWebserver = Boolean.valueOf(properties.getProperty("enableWebserver", "true"));
		opcuaEnabled = Boolean.valueOf(properties.getProperty("opcuaEnabled", "false"));
		opcuaPort = Integer.valueOf(properties.getProperty("opcuaPort", "4840"));

		ticketEnabled = Boolean.valueOf(properties.getProperty("ticketEnabled", "false"));
		ticketHost = properties.getProperty("ticketHost", "");
		ticketPort = Integer.valueOf(properties.getProperty("ticketPort", "80"));
		ticketUser = properties.getProperty("ticketUser", "");
		ticketType = properties.getProperty("ticketType", "");
		ticketWorkerGroup = properties.getProperty("ticketWorkerGroup", "");
		ticketAccessToken = properties.getProperty("ticketAccessToken", "");

		logLevel = properties.getProperty("logLevel", "INFO");
		logLevelAPIs = properties.getProperty("logLevelAPIs", "ERROR");
		verbose = Boolean.valueOf(properties.getProperty("verbose", "false"));
		sparkFileDir = properties.getProperty("sparkFileDir", "httpdocs");
		sparkPort = properties.getProperty("sparkPort", "4567");
		sparkSSL = properties.getProperty("sparkSSL", "false");
		keystoreFilePath = properties.getProperty("keystoreFilePath", "keystore.jks");
		keystorePassword = properties.getProperty("keystorePassword", "--");
		publicServer = properties.getProperty("publicServer", "false");
		useUsernames = Boolean.valueOf(properties.getProperty("useUsernames", "false"));
		standardUser = properties.getProperty("standardUser", "--");
		if (standardUser.equals("--"))
			standardUser = "all";
		allowDeleteData = properties.getProperty("allowDeleteData", "false");
		accessControl = Boolean.valueOf(properties.getProperty("accessControl", "false"));
		whiteList = properties.getProperty("whiteList", "127.0.0.1,0:0:0:0:0:0:0:1");

		outboundMailServer = properties.getProperty("outboundMailServer", "smtp.server.de");
		outboundMailServerPort = Integer.valueOf(properties.getProperty("outboundMailServerPort", "587"));
		mailserverUser = properties.getProperty("mailserverUser", "user");
		mailserverPassword = properties.getProperty("mailserverPassword", "password");

		dbPersistValue = Boolean.valueOf(properties.getProperty("dbPersistValue", "true"));
		dbPath = properties.getProperty("dbPath", "localhost");
		dbPort = properties.getProperty("dbPort", "27017");
		dbUser = properties.getProperty("dbUser", "--");
		dbPass = properties.getProperty("dbPass", "--");
		dbName = properties.getProperty("dbName", "owcore");
		dbGenericPrefix = properties.getProperty("dbGenericPrefix", "configs");
		dbSensorPrefix = properties.getProperty("dbSensorPrefix", "sensors");
		dbContainerPrefix = properties.getProperty("dbSensorPrefix", "container");
		dbArchivePrefix = properties.getProperty("dbArchivePrefix", "archive");
		idSeperator = properties.getProperty("idSeperator", "---");
		baseTimeInterval = Long.valueOf(properties.getProperty("baseTimeInterval", "3600000"));
		referenceDBName = properties.getProperty("referenceDBName", "references");
		maxConnectionsPerHost = Integer.valueOf(properties.getProperty("maxConnectionsPerHost", "16"));
		connectionQueueSizeMultiplier = Integer.valueOf(properties.getProperty("connectionQueueSizeMultiplier", "1"));

		mappingsCollection = properties.getProperty("mappingsCollection", "idMappings");

		rmqPath = properties.getProperty("rmqPath", "localhost");
		rmqQueue = properties.getProperty("rmqQueue", "odash");
		rmqQueueAutoDelete = Boolean.valueOf(properties.getProperty("rmqQueueAutoDelete", "false"));
		rmqUser = properties.getProperty("rmqUser", "--");
		rmqPass = properties.getProperty("rmqPass", "--");
		rmqPort = properties.getProperty("rmqPort", "5672");
		rmqExchange = properties.getProperty("rmqExchange", "amq.topic");
		rmqTopic = properties.getProperty("rmqTopic", "#");
		validateData = properties.getProperty("validateData", "false");
		requeueUnvalidated = properties.getProperty("requeueUnvalidated", "false");
		publishParsedData = Boolean.valueOf(properties.getProperty("publishParsedData", "false"));
		rmqvHost = properties.getProperty("rmqvHost", "/");

		mqttAdresse = properties.getProperty("mqttAdresse", "localhost");
		mqttPort = Integer.valueOf(properties.getProperty("mqttPort", "1883"));
		mqttTopic = properties.getProperty("mqttTopic", "openinc");

		analyticSensors = properties.getProperty("analyticSensors", "analyticSensors");
		analyticOperationCollection = properties.getProperty("analyticOperationCollection", "analyticOperations");
		aggregationServiceUrl = properties.getProperty("aggregationServiceUrl", "localhost");
		aggregationServicePort = properties.getProperty("aggregationServicePort", "8080");
		analyticPrefix = properties.getProperty("analyticPrefix", "analytic.");
		try {
			analyticRefreshRate = Long.valueOf(properties.getProperty("analyticRefreshRate", "60000"));
		} catch (NumberFormatException e) {
			analyticRefreshRate = 60000l;
		}

		if (dbPass.equals("--") || rmqPass.equals("--")) {
			OpenWareInstance.getInstance().logInfo("Info: DB or RMQ password seems to be undefined.");
		}

		webhookClass = properties.getProperty("webhookClass", "OD3AlarmWebhook");
		templatingRegexSelector = properties.getProperty("templatingRegexSelector", "\\{\\{(.*?)\\}\\}");
		templatingSectionRegexSelector = properties.getProperty("templatingSectionRegexSelector",
				"\\{\\{##(.*?)##\\}\\}");
	}

	public static JSONObject mapId(String external) {
		return idMappings.get(external);
	}
}

package de.openinc.ow;

import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.externalStaticFileLocation;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.init;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.secure;
import static spark.Spark.stop;
import static spark.Spark.webSocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.TimeZone;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.api.AnalyticSensorProvider;
import de.openinc.api.AnalyticsProvider;
import de.openinc.api.DataHandler;
import de.openinc.api.OWServiceActivator;
import de.openinc.api.OpenWareAPI;
import de.openinc.api.OpenWarePlugin;
import de.openinc.api.PersistenceAdapter;
import de.openinc.api.ReferenceAdapter;
import de.openinc.api.ReportInterface;
import de.openinc.api.TransformationOperation;
import de.openinc.api.UserAdapter;
import de.openinc.model.user.User;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.http.AdminAPI;
import de.openinc.ow.http.AlarmAPI;
import de.openinc.ow.http.AnalyticsServiceAPI;
import de.openinc.ow.http.MiddlewareApi;
import de.openinc.ow.http.ReferenceAPI;
import de.openinc.ow.http.ReportsAPI;
import de.openinc.ow.http.SubscriptionProvider;
import de.openinc.ow.http.TransformationAPI;
import de.openinc.ow.http.UserAPI;
import de.openinc.ow.middleware.services.AnalyticsService;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.OWService;
import de.openinc.ow.middleware.services.ReportsService;
import de.openinc.ow.middleware.services.ServiceRegistry;
import de.openinc.ow.middleware.services.TransformationService;
import de.openinc.ow.middleware.services.UserService;

public class OpenWareInstance {
	/**
	 * API constant to setup a websocket subscription
	 */
	public static final String LIVE_API = "/subscription";

	public String VERSION = "1.00";

	protected Logger logger;

	Logger infoLogger;
	Logger errorLogger;
	Logger mqttLogger;

	LoggerConfig apacheLogger;
	LoggerConfig sparkLogger;
	LoggerConfig jettyLogger;
	LoggerConfig mongoLogger;
	LoggerConfig xdocLogger;
	Logger apiLogger;
	Logger dataLogger;
	private LoggerContext ctx;
	
	private static OpenWareInstance me;
	private ArrayList<OpenWareAPI> services;
	private boolean running = false;
	private JSONObject state;

	public void logData(long ts, String topic, String msg) {
		this.dataLogger.info("",""+ts, topic,msg);
	}
	public void logInfo(Object info) {

		this.infoLogger.info(info);

	}

	public void logMQTT(Object info) {

		this.mqttLogger.info(info);

	}

	public void logDebug(Object debug) {

		this.infoLogger.debug(debug);

	}

	public void logDebug(Object debug, Throwable t) {

		infoLogger.debug(debug, t);

	}

	public void logTrace(Object trace) {

		infoLogger.trace(trace);

	}

	public void logWarn(Object warn) {

		errorLogger.warn(warn);

	}

	public void logError(Object error) {

		errorLogger.error(error);

	}

	public void logError(Object error, Throwable t) {

		errorLogger.error(error, t);

	}

	private OpenWareInstance() {
		//this.logger = LogManager.getLogger("main");
		//this.errorlogger = LogManager.getLogger("errorLogger");

		//this.infoLogger = LogManager.getRootLogger();
		ctx= (LoggerContext) LogManager.getContext();

		this.apacheLogger = ctx.getConfiguration().getLoggerConfig("org.apache.http");
		this.sparkLogger = ctx.getConfiguration().getLoggerConfig("spark");
		this.jettyLogger = ctx.getConfiguration().getLoggerConfig("org.eclipse.jetty");
		this.mongoLogger =ctx.getConfiguration().getLoggerConfig("org.mongodb.driver");
		this.xdocLogger = ctx.getConfiguration().getLoggerConfig("fr.opensagres.xdocreport");
		
		me = this;
		this.services = new ArrayList<OpenWareAPI>();

		Config.init();
		Configuration conf = ctx.getConfiguration();
		LoggerConfig logconf = conf.getLoggerConfig("openware");
		LoggerConfig mqttconf = conf.getLoggerConfig("mqttLogger");
		switch (Config.get("logLevel", "INFO")) {
		case "TRACE":
			logconf.setLevel(Level.TRACE);
			mqttconf.setLevel(Level.INFO);
			break;
		case "DEBUG":
			logconf.setLevel(Level.DEBUG);
			mqttconf.setLevel(Level.INFO);
		case "OFF":
			logconf.setLevel(Level.OFF);
			mqttconf.setLevel(Level.OFF);
			break;
		case "INFO":
			logconf.setLevel(Level.INFO);
			mqttconf.setLevel(Level.INFO);
			break;
		case "ERROR":
			logconf.setLevel(Level.ERROR);
			mqttconf.setLevel(Level.OFF);
			break;
		case "WARN":
			logconf.setLevel(Level.WARN);
			mqttconf.setLevel(Level.INFO);
			break;
		case "ALL":
			logconf.setLevel(Level.ALL);
			mqttconf.setLevel(Level.INFO);
			break;
		default:
			logconf.setLevel(Level.INFO);
			break;
		}

		switch (Config.get("logLevelAPIs", "OFF")) {
		case "TRACE":
			apacheLogger.setLevel(Level.TRACE);
			sparkLogger.setLevel(Level.TRACE);
			jettyLogger.setLevel(Level.TRACE);
			mongoLogger.setLevel(Level.TRACE);
			xdocLogger.setLevel(Level.TRACE);

			break;
		case "DEBUG":
			apacheLogger.setLevel(Level.DEBUG);
			sparkLogger.setLevel(Level.DEBUG);
			jettyLogger.setLevel(Level.DEBUG);
			mongoLogger.setLevel(Level.DEBUG);
			xdocLogger.setLevel(Level.DEBUG);
			break;
		case "OFF":
			apacheLogger.setLevel(Level.OFF);
			sparkLogger.setLevel(Level.OFF);
			jettyLogger.setLevel(Level.OFF);
			mongoLogger.setLevel(Level.OFF);
			xdocLogger.setLevel(Level.OFF);
			break;
		case "INFO":
			apacheLogger.setLevel(Level.INFO);
			sparkLogger.setLevel(Level.INFO);
			jettyLogger.setLevel(Level.INFO);
			mongoLogger.setLevel(Level.INFO);
			xdocLogger.setLevel(Level.INFO);
			break;
		case "ERROR":
			apacheLogger.setLevel(Level.ERROR);
			sparkLogger.setLevel(Level.ERROR);
			jettyLogger.setLevel(Level.ERROR);
			mongoLogger.setLevel(Level.ERROR);
			xdocLogger.setLevel(Level.ERROR);
			break;
		case "WARN":
			apacheLogger.setLevel(Level.WARN);
			sparkLogger.setLevel(Level.WARN);
			jettyLogger.setLevel(Level.WARN);
			mongoLogger.setLevel(Level.WARN);
			xdocLogger.setLevel(Level.WARN);
			break;
		case "ALL":
			apacheLogger.setLevel(Level.ALL);
			sparkLogger.setLevel(Level.ALL);
			jettyLogger.setLevel(Level.ALL);
			mongoLogger.setLevel(Level.ALL);
			xdocLogger.setLevel(Level.ALL);
			break;
		default:
			apacheLogger.setLevel(Level.WARN);
			sparkLogger.setLevel(Level.WARN);
			jettyLogger.setLevel(Level.WARN);
			mongoLogger.setLevel(Level.WARN);
			xdocLogger.setLevel(Level.WARN);
			break;
		}
		ctx.updateLoggers();
		this.infoLogger = ctx.getLogger("openware");
		this.errorLogger = ctx.getLogger("errorLogger");
		this.mqttLogger = ctx.getLogger("mqttLogger");
		this.apiLogger = ctx.getLogger("apiLogger");
		this.dataLogger = ctx.getLogger("dataLogger");

		OpenWareInstance.getInstance().logError("--------------------------------------------------------------");
		OpenWareInstance.getInstance().logError("---------------------Restart Backend--------------------------");
		OpenWareInstance.getInstance().logError("--------------------------------------------------------------");

		OpenWareInstance.getInstance().logInfo("Reading config file spark.properties");

		TimeZone zone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone(Config.get("timezone","Europe/Berlin")));
		zone = TimeZone.getDefault();
		
		if (Config.getBool("enableWebserver", true) ) {
			logInfo("Initializing open.WARE v" +	OpenWareInstance.getInstance().VERSION +
					" on port " +
					Config.getInt("sparkPort",4567) );
			port(Integer.valueOf(Config.getInt("sparkPort",4567)));

			if (Config.getBool("sparkSSL", false)) {
				OpenWareInstance.getInstance().logInfo(
						"Setting up encryption. Warning: keystore file expires and has to be manually replaced.");
				secure(Config.get("keystoreFilePath","."), Config.get("keystorePassword", "pass"), null, null);
			}
		}

		//------------------- Services & API -------------------- 
		UserService userService = UserService.getInstance();
		try {
			userService.setAdapter(loadUserAdapter());	
		}catch(Exception e) {
			logError("Could not load User Adapter or no UserAdapter provided!", e);
			System.exit(0);
		}
		

		DataService.init();
		DataService.setPersistenceAdapter(loadPersistenceAdapter());
		DataService.setReferenceAdapter(loadReferenceAdapterAdapter());

		AnalyticsService.getInstance().setSensorProvider(loadAnalyticSensorProvider());

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Middleware loading...");
		// Middleware Data API
		MiddlewareApi middlewareApi = new MiddlewareApi();
		OpenWareInstance.getInstance().registerService(middlewareApi);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Userservice loading...");
		// UserManagement API
		UserAPI userAPI = new UserAPI();
		OpenWareInstance.getInstance().registerService(userAPI);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Analyticservice loading...");
		// AnalyticsService API
		AnalyticsServiceAPI asa = new AnalyticsServiceAPI();
		OpenWareInstance.getInstance().registerService(asa);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Admin API loading...");
		// Admin API
		AdminAPI adminApi = new AdminAPI();
		OpenWareInstance.getInstance().registerService(adminApi);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Reportservice loading...");
		//ReportsAPI
		ReportsAPI rApi = new ReportsAPI();
		OpenWareInstance.getInstance().registerService(rApi);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Alarmservice loading...");
		// Alarm & Event API
		AlarmAPI as = AlarmAPI.getInstance();
		OpenWareInstance.getInstance().registerService(as);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Transformationservice loading...");
		//TransformationAPI
		TransformationAPI tApi = new TransformationAPI();
		OpenWareInstance.getInstance().registerService(tApi);

		OpenWareInstance.getInstance().logTrace("[SERVICE API] " + "Referenceservice loading...");
		//ReferenceAPI
		ReferenceAPI refApi = new ReferenceAPI();
		OpenWareInstance.getInstance().registerService(refApi);

		OpenWareInstance.getInstance()
				.logTrace("[PlUGINS] " + "------------------Plugins loading...-------------------------");
		//Plugins
		loadPlugins();
		OpenWareInstance.getInstance()
				.logTrace("[PLUGINS] " + "------------------Plugins loaded...-------------------------");
		OpenWareInstance.getInstance().logInfo("Using external file path: " + Config.get("publicHTTP", "app"));
		externalStaticFileLocation(Config.get("publicHTTP", "app")); // index.html is served at localhost:4567 (default port)
		webSocket(LIVE_API, SubscriptionProvider.class);

		after((req, res) -> {
			if (req.url().contains("/api/transform")) {

				System.gc();
			}

		});

		exception(Exception.class, (e, req, res) -> {
			JSONObject details = new JSONObject();

			JSONObject params = new JSONObject();
			for (String key : req.params().keySet()) {
				params.put(key, req.params(key));
			}
			details.put("urlParams", params);

			JSONObject queryP = new JSONObject();
			for (String key : req.queryMap().toMap().keySet()) {
				queryP.put(key, req.params(key));
			}
			User user = (User) req.session().attribute("user");
			JSONObject uInfo = new JSONObject();
			if (user != null) {
				uInfo = user.toJSON();
			}
			details.put("queryParams", queryP);
			details.put("url", req.url());
			details.put("user", uInfo);

			OpenWareInstance.getInstance().logError("Error on " +	req.url() +
													": " +
													e.getLocalizedMessage() +
													"\n" +
													details.toString(2),
					e);
			HTTPResponseHelper.generateResponse(res, 400, null, e.getMessage());
		});

		path("/api", () -> {
			for (OpenWareAPI os : services) {
				os.registerRoutes();
			}
		});

		before("/api/*", (request, response) -> {
			apiLogger.debug("[API-ACCESS][SOURCE:" +	request.ip() +
							"][" +
							request.requestMethod() +
							"]" +
							request.pathInfo());
			response.header("Access-Control-Allow-Origin", "*");

			if (request.requestMethod().equals("OPTIONS")) {
				String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
				if (accessControlRequestHeaders != null) {
					response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
				}

				String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
				if (accessControlRequestMethod != null) {
					response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
				}

				response.status(200);
				return;
			}
			
			// request.session(true);
			if (Config.getBool("accessControl", true)) {
				boolean authorized = false;
				User user;
				if(request.queryParams().contains("username")&&request.queryParams().contains("password")){
					user = UserService.getInstance().login(request.queryParams("username"), request.queryParams("password"));
				}else {
					user = UserService.getInstance().checkAuth(request.headers(UserAPI.OD_SESSION));	
				}
				
				
				if (request.headers().contains("Authorization") && request.headers("Authorization").startsWith("Bearer ")) {
					user = UserService.getInstance().jwtToUser(request.headers("Authorization").substring(7));
					request.session().attribute("apiaccess", true);
				}
				authorized = user != null;
				request.session().attribute("user", user);

				if (!authorized) {
					halt(HTTPResponseHelper.generateResponse(response,HTTPResponseHelper.STATUS_FORBIDDEN, null, "Not authorized").toString());
					
				}
			}
		}

		);
		String index;
		try {
			index = new String(Files.readAllBytes(Paths.get( Config.get("publicHTTP", "app") + "/index.html")));
			get("*", (req, res) -> {
				if (!req.pathInfo().startsWith("/static") && !req.pathInfo().startsWith("/subscription")) {
					res.status(404);
					return index;
				}
				return null;
			});
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("INSTANCE CREATED");
	}

	private AnalyticSensorProvider loadAnalyticSensorProvider() {
		ServiceLoader<AnalyticSensorProvider> loader = ServiceLoader.load(AnalyticSensorProvider.class);
		try {
			AnalyticSensorProvider provider = loader.iterator().next();
			return provider;
		} catch (NoSuchElementException e) {
			return null;
		}

	}

	public static OpenWareInstance getInstance() {
		if (me == null) {
			me = new OpenWareInstance();
		}
		return me;
	}

	public void registerService(OpenWareAPI service) {
		services.add(service);

		if (isRunning()) {
			OpenWareInstance.getInstance().logError(
					"Server already running! You need to restart the Instance to access newly registered Services");
		}
	}

	public JSONObject getState() {
		JSONObject state = new JSONObject();
		ServiceRegistry registry = ServiceRegistry.getInstance();
		JSONArray activeServices = new JSONArray();
		JSONArray inactiveServices = new JSONArray();
		for (OWService service : registry.getActiveServices()) {
			activeServices.put(service.toJSONObject());
		}
		for (OWService service : registry.getInactiveServices()) {
			inactiveServices.put(service.toJSONObject());
		}
		JSONObject cServices = new JSONObject();
		cServices.put("active", activeServices);
		cServices.put("inactive", inactiveServices);
		state.put("services", cServices);
		state.put("pesistence", DataService.getStats());
		return state;
	}

	public void startInstance() {

		if (!isRunning()) {
			init();
			OpenWareInstance.getInstance().logInfo("Started WebServer...");
		}
		setRunning(true);
	}

	public void stopInstance() {
		if (isRunning()) {
			stop();
		}
		setRunning(false);
	}

	public boolean isRunning() {
		return this.running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	private void loadPlugins() {
		loadOWPlugins();
		loadHTTPAPI();
		loadAnalyticsProvider();
		loadReportTypes();
		loadTransformationOperations();
		loadDataHandler();
		loadActuators();

	}

	private void loadDataHandler() {
		logInfo("------- 		Loading Data Handler 		------");
		ServiceLoader<DataHandler> loader = ServiceLoader.load(DataHandler.class);
		Iterator<DataHandler> it = loader.iterator();

		while (it.hasNext()) {
			DataHandler handler = it.next();
			OWService aHandler = new OWService(handler.getClass().getCanonicalName(), handler,
					new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							return DataService.removeHandler(handler) != null;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							if (prevInstance != null) {
								DataService.removeHandler((DataHandler) prevInstance);
							}
							handler.setOptions(options);
							DataService.addHandler(handler);
							logInfo(handler.getClass().getCanonicalName() + " loaded!");
							return handler;
						}
					});
			if (!aHandler.isDeactivated()) {
				try {
					aHandler.load(null);
				} catch (Exception e) {
					logError("Could not load DataHandler " + aHandler.getClass().getCanonicalName(), e);
				}
			}

		}
	}

	private void loadTransformationOperations() {
		logInfo("------- 		Loading Tranformation Operations 		------");
		ServiceLoader<TransformationOperation> loader = ServiceLoader.load(TransformationOperation.class);
		Iterator<TransformationOperation> it = loader.iterator();
		while (it.hasNext()) {
			TransformationOperation op = it.next();
			OWService anOperation = new OWService(op.getClass().getCanonicalName(), op,
					new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							TransformationService.getInstance().removeOperation(op.getClass());
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							if (prevInstance != null) {
								TransformationService.getInstance().removeOperation(op.getClass());
							}
							TransformationService.getInstance().registerOperation(op.getClass());
							logInfo(op.getClass().getCanonicalName() + " loaded!");
							return op;
						}
					});
			if (!anOperation.isDeactivated()) {
				try {
					anOperation.load(null);
				} catch (Exception e) {
					logError("Could not load TransformationOperation " + anOperation.getClass().getCanonicalName(), e);
				}
			}
		}
	}

	private void loadActuators() {
		logInfo("------- 		Loading Actuators 		------");
		ServiceLoader<ActuatorAdapter> loader = ServiceLoader.load(ActuatorAdapter.class);
		Iterator<ActuatorAdapter> it = loader.iterator();
		while (it.hasNext()) {
			ActuatorAdapter actor = it.next();
			OWService anActuator = new OWService(actor.getClass().getCanonicalName(), actor,
					new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							DataService.remove(actor);
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							actor.init(options, true);
							DataService.addActuator(actor);
							logInfo(actor.getClass().getCanonicalName() + " loaded!");
							return actor;
						}
					});
			if (!anActuator.isDeactivated()) {
				try {
					anActuator.load(null);
				} catch (Exception e) {
					logError("Could not load Actuator " + anActuator.getClass().getCanonicalName(), e);
				}
			}
		}
	}

	private void loadReportTypes() {
		logInfo("------- 			Loading Report Types 				------");
		ServiceLoader<ReportInterface> loader = ServiceLoader.load(ReportInterface.class);
		Iterator<ReportInterface> it = loader.iterator();
		while (it.hasNext()) {
			ReportInterface provider = it.next();
			OWService aProvider = new OWService(provider.getClass().getCanonicalName(), provider,
					new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							ReportsService.getInstance().removeReportType(provider.getTag());
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							if (prevInstance != null) {
								ReportsService.getInstance().removeReportType(provider.getTag());
							}
							ReportsService.getInstance().addReportType(provider.getTag(),
									(Class<ReportInterface>) provider.getClass());
							logInfo(provider.getClass().getCanonicalName() + " loaded!");
							return provider;
						}
					});
			if (!aProvider.isDeactivated()) {
				try {
					aProvider.load(null);
				} catch (Exception e) {
					logError("Could not load ReportType " + aProvider.getClass().getCanonicalName(), e);
				}
			}

		}
	}

	// V-Sensor
	private void loadAnalyticsProvider() {
		logInfo("------- 			Loading Analytic Providers			------");
		ServiceLoader<AnalyticsProvider> loader = ServiceLoader.load(AnalyticsProvider.class);
		Iterator<AnalyticsProvider> it = loader.iterator();
		while (it.hasNext()) {
			AnalyticsProvider provider = it.next();
			OWService aProviderService = new OWService(provider.getClass().getCanonicalName(), provider,
					new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							AnalyticsService.getInstance().deregisterAnalyticsProvider(provider.getOID());
							logInfo(provider.getClass().getCanonicalName() + " loaded!");
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							AnalyticsService.getInstance().registerAnalyticsProvider(provider.getOID(), provider);
							logInfo(provider.getClass().getCanonicalName() + " loaded!");
							return provider;
						}
					});
			if (!aProviderService.isDeactivated()) {
				try {
					aProviderService.load(null);
				} catch (Exception e) {
					logError("Could not load AnalyticsProvider " + aProviderService.getClass().getCanonicalName(), e);
				}
			}
		}
	}

	private void loadHTTPAPI() {
		logInfo("------- 				Loading HTTP APIs 				------");
		ServiceLoader<OpenWareAPI> loader = ServiceLoader.load(OpenWareAPI.class);
		Iterator<OpenWareAPI> it = loader.iterator();
		while (it.hasNext()) {
			OpenWareAPI api = it.next();
			registerService(api);
			logInfo(api.getClass().getCanonicalName() + " loaded!");
		}
	}

	private UserAdapter loadUserAdapter() throws Exception{
		logInfo("------- 				Loading User Adapter			------");
		ServiceLoader<UserAdapter> loader = ServiceLoader.load(UserAdapter.class);
			UserAdapter adapter = loader.iterator().next();
			logInfo(adapter.getClass().getCanonicalName() + " loaded!");
			return adapter;
		

	}

	private PersistenceAdapter loadPersistenceAdapter() {
		logInfo("------- 			Loading Persistence Adapter			------");
		ServiceLoader<PersistenceAdapter> loader = ServiceLoader.load(PersistenceAdapter.class);
		try {
			PersistenceAdapter adapter = loader.iterator().next();
			logInfo(adapter.getClass().getCanonicalName() + " loaded!");
			return adapter;
		} catch (NoSuchElementException e) {
			return null;
		}

	}

	private ReferenceAdapter loadReferenceAdapterAdapter() {
		logInfo("------- 			Loading Reference Adapter			------");
		ServiceLoader<ReferenceAdapter> loader = ServiceLoader.load(ReferenceAdapter.class);
		try {
			ReferenceAdapter adapter = loader.iterator().next();
			logInfo(adapter.getClass().getCanonicalName() + " loaded!");
			return adapter;
		} catch (NoSuchElementException e) {
			return null;
		}

	}

	private void loadOWPlugins() {
		logInfo("------- 				Loading OW Plugins			------");
		ServiceLoader<OpenWarePlugin> pluginLoader = ServiceLoader.load(OpenWarePlugin.class);
		for (OpenWarePlugin plugin : pluginLoader) {
			try {
				OWService pluginService = new OWService(plugin.getClass().getCanonicalName(),
						plugin, new OWServiceActivator() {

							@Override
							public boolean unload() throws Exception {
								plugin.disable();
								return true;
							}

							@Override
							public Object load(Object prevInstance, JSONObject options) throws Exception {
								plugin.init(OpenWareInstance.getInstance(), options);
								logInfo(plugin.getClass().getCanonicalName() + " loaded!");
								return plugin;
							}
						});

				if (!pluginService.isDeactivated())
					pluginService.load(null);
			} catch (Exception e) {
				logError("Error while loading Plugin " + plugin.getClass().getCanonicalName(), e);
			}
		}
	}

}

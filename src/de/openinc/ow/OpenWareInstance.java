package de.openinc.ow;

import static io.javalin.apibuilder.ApiBuilder.after;
import static io.javalin.apibuilder.ApiBuilder.before;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.ws;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

import de.openinc.api.ActuatorAdapter;
import de.openinc.api.AnalyticSensorProvider;
import de.openinc.api.DataHandler;
import de.openinc.api.OWService;
import de.openinc.api.OWServiceActivator;
import de.openinc.api.OpenWareAPI;
import de.openinc.api.OpenWarePlugin;
import de.openinc.api.PersistenceAdapter;
import de.openinc.api.ReferenceAdapter;
import de.openinc.api.ReportInterface;
import de.openinc.api.TransformationOperation;
import de.openinc.api.UserAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareDataItemSerializer;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.model.user.User;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.DataTools;
import de.openinc.ow.http.AdminAPI;
import de.openinc.ow.http.AlarmAPI;
import de.openinc.ow.http.AnalyticsServiceAPI;
import de.openinc.ow.http.JavalinWebsocketProvider;
import de.openinc.ow.http.MiddlewareApi;
import de.openinc.ow.http.ReferenceAPI;
import de.openinc.ow.http.ReportsAPI;
import de.openinc.ow.http.TransformationAPI;
import de.openinc.ow.http.UserAPI;
import de.openinc.ow.middleware.services.AnalyticsService;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.ReportsService;
import de.openinc.ow.middleware.services.ServiceRegistry;
import de.openinc.ow.middleware.services.TransformationService;
import de.openinc.ow.middleware.services.UserService;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;

public class OpenWareInstance {
	/**
	 * API constant to setup a websocket subscription
	 */
	public static final String LIVE_API = "/subscription";

	public String VERSION = "2.00";

	protected Logger logger;

	Logger infoLogger;
	Logger errorLogger;
	Logger mqttLogger;
	Logger accessLogger;
	Logger persistenceLogger;
	Logger mailLogger;
	Logger apiLogger;
	Logger dataLogger;

	LoggerConfig apacheLogger;
	LoggerConfig sparkLogger;
	LoggerConfig jettyLogger;
	LoggerConfig mongoLogger;
	LoggerConfig xdocLogger;
	LoggerConfig dataJSONLogger;
	private LoggerContext ctx;
	private ScheduledExecutorService commonExecuteService;
	private static OpenWareInstance me;
	private ArrayList<OpenWareAPI> services;
	private boolean running = false;

	private CompletableFuture<Boolean> started;
	private Javalin javalinInstance;
	private Gson gson;

	public void logData(String current, long ts, String topic, String msg) {

		this.dataLogger.info("", current, "" + ts, topic, msg);
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

	public void logMail(Object info) {

		mailLogger.info(info);

	}

	public void logError(Object error) {

		errorLogger.error(error);

	}

	public void logError(Object error, Throwable t) {

		errorLogger.error(error, t);

	}

	public void logPersistence(Object msg) {

		persistenceLogger.trace(msg);

	}

	public CompletableFuture<Boolean> awaitInitialization() {
		return started;
	}

	private OpenWareInstance() {
		this.started = new CompletableFuture<Boolean>();
		ctx = (LoggerContext) LogManager.getContext();
		ThreadFactory factory = new ThreadFactoryBuilder()	.setDaemon(false)
															.setNameFormat("openware-commonpool-%d")
															.build();
		this.commonExecuteService = Executors.newScheduledThreadPool(4, factory);
		this.apacheLogger = ctx	.getConfiguration()
								.getLoggerConfig("org.apache.http");
		this.sparkLogger = ctx	.getConfiguration()
								.getLoggerConfig("spark");
		this.jettyLogger = ctx	.getConfiguration()
								.getLoggerConfig("org.eclipse.jetty");
		this.mongoLogger = ctx	.getConfiguration()
								.getLoggerConfig("org.mongodb.driver");
		this.xdocLogger = ctx	.getConfiguration()
								.getLoggerConfig("fr.opensagres.xdocreport");

		me = this;
		this.services = new ArrayList<OpenWareAPI>();

		Config.init();
		Configuration conf = ctx.getConfiguration();
		LoggerConfig logconf = conf.getLoggerConfig("openware");
		LoggerConfig mqttconf = conf.getLoggerConfig("mqttLogger");
		LoggerConfig dataconf = conf.getLoggerConfig("dataLogger");
		switch (Config.get("logLevel", "INFO")) {
		case "TRACE":
			logconf.setLevel(Level.TRACE);
			mqttconf.setLevel(Level.TRACE);
			dataconf.setLevel(Level.TRACE);
			break;
		case "DEBUG":
			logconf.setLevel(Level.DEBUG);
			mqttconf.setLevel(Level.INFO);
			dataconf.setLevel(Level.INFO);
		case "OFF":
			logconf.setLevel(Level.OFF);
			mqttconf.setLevel(Level.OFF);
			dataconf.setLevel(Level.OFF);
			;
			break;
		case "INFO":
			logconf.setLevel(Level.INFO);
			mqttconf.setLevel(Level.INFO);
			dataconf.setLevel(Level.INFO);
			break;
		case "ERROR":
			logconf.setLevel(Level.ERROR);
			mqttconf.setLevel(Level.OFF);
			dataconf.setLevel(Level.OFF);
			break;
		case "WARN":
			logconf.setLevel(Level.WARN);
			mqttconf.setLevel(Level.INFO);
			dataconf.setLevel(Level.INFO);
			break;
		case "ALL":
			logconf.setLevel(Level.ALL);
			mqttconf.setLevel(Level.INFO);
			dataconf.setLevel(Level.INFO);
			break;
		default:
			logconf.setLevel(Level.INFO);
			mqttconf.setLevel(Level.INFO);
			dataconf.setLevel(Level.INFO);
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
		this.accessLogger = ctx.getLogger("accessLogger");
		this.mailLogger = ctx.getLogger("mailLogger");
		this.persistenceLogger = ctx.getLogger("persistenceLogger");

		OpenWareInstance.getInstance()
						.logError("--------------------------------------------------------------");
		OpenWareInstance.getInstance()
						.logError("---------------------Restart Backend--------------------------");
		OpenWareInstance.getInstance()
						.logError("--------------------------------------------------------------");

		OpenWareInstance.getInstance()
						.logInfo("Reading config file spark.properties");

		TimeZone zone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone(Config.get("timezone", "Europe/Berlin")));
		String[] localeElements = Config.get("language", "de-de")
										.split("-");
		if (localeElements.length > 1) {
			Locale.setDefault(new Locale(localeElements[0], localeElements[1]));

		} else {
			Locale.setDefault(new Locale(localeElements[0]));
		}
		logInfo("Locale set to " + Locale	.getDefault()
											.toLanguageTag());

		logInfo("Initializing open.WARE v" + OpenWareInstance.getInstance().VERSION + " on port "
				+ Config.getInt("sparkPort", 4567));

		// ------------------- Services & API --------------------
		UserService userService = UserService.getInstance();
		try {
			userService.setAdapter(loadUserAdapter());
		} catch (Exception e) {
			logError("Could not load User Adapter or no UserAdapter provided!", e);
			System.exit(0);
		}

		DataService.init();
		DataService.setPersistenceAdapter(loadPersistenceAdapter());
		DataService.setReferenceAdapter(loadReferenceAdapterAdapter());
		OpenWareInstance.getInstance()
						.logTrace("[ANALYTICS API] " + "Middleware loading...");
		loadAnalyticSensorProvider();

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Middleware loading...");
		// Middleware Data API
		MiddlewareApi middlewareApi = new MiddlewareApi();
		OpenWareInstance.getInstance()
						.registerService(middlewareApi);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Userservice loading...");
		// UserManagement API
		UserAPI userAPI = new UserAPI();
		OpenWareInstance.getInstance()
						.registerService(userAPI);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Analyticservice loading...");
		// AnalyticsService API
		AnalyticsServiceAPI asa = new AnalyticsServiceAPI();
		OpenWareInstance.getInstance()
						.registerService(asa);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Admin API loading...");
		// Admin API
		AdminAPI adminApi = new AdminAPI();
		OpenWareInstance.getInstance()
						.registerService(adminApi);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Reportservice loading...");
		// ReportsAPI
		ReportsAPI rApi = new ReportsAPI();
		OpenWareInstance.getInstance()
						.registerService(rApi);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Alarmservice loading...");
		// Alarm & Event API
		AlarmAPI as = AlarmAPI.getInstance();
		OpenWareInstance.getInstance()
						.registerService(as);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Transformationservice loading...");
		// TransformationAPI
		TransformationAPI tApi = new TransformationAPI();
		OpenWareInstance.getInstance()
						.registerService(tApi);

		OpenWareInstance.getInstance()
						.logTrace("[SERVICE API] " + "Referenceservice loading...");
		// ReferenceAPI
		ReferenceAPI refApi = new ReferenceAPI();
		OpenWareInstance.getInstance()
						.registerService(refApi);

		OpenWareInstance.getInstance()
						.logInfo("Using external file path: " + Config.get("publicHTTP", "app"));
		// Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		gson = new GsonBuilder().registerTypeAdapter(OpenWareDataItem.class, new OpenWareDataItemSerializer())
								.registerTypeAdapter(JSONObject.class, new JsonSerializer<JSONObject>() {

									@Override
									public JsonElement serialize(JSONObject src, Type typeOfSrc,
											JsonSerializationContext context) {
										JsonElement elemt;
										try {
											elemt = JsonParser	.parseString(src.toString())
																.getAsJsonObject();
											return elemt;
										} catch (JsonSyntaxException e) {
											e.printStackTrace();
											return null;
										}

									}

								})
								.registerTypeAdapter(JSONArray.class, new JsonSerializer<JSONArray>() {

									@Override
									public JsonElement serialize(JSONArray src, Type typeOfSrc,
											JsonSerializationContext context) {
										JsonElement elemt;
										try {
											elemt = JsonParser	.parseString(src.toString())
																.getAsJsonArray();
											return elemt;
										} catch (JsonSyntaxException e) {
											e.printStackTrace();
											return null;
										}

									}

								})
								.create();

		JsonMapper gsonMapper = new JsonMapper() {
			@Override
			public String toJsonString(@NotNull Object obj, @NotNull Type type) {
				return gson.toJson(obj, type);
			}

			@Override
			public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
				return gson.fromJson(json, targetType);
			}
		};

		javalinInstance = Javalin.create(config -> {

			config.staticFiles.add(staticFiles -> {
				staticFiles.location = Location.EXTERNAL;
				staticFiles.directory = Config.get("publicHTTP", "app");
			});

			config.spaRoot.addFile("/", Config.get("publicHTTP", "app") + "/index.html", Location.EXTERNAL);
			config.http.defaultContentType = "application/json";
			config.jsonMapper(gsonMapper);
			config.http.gzipOnlyCompression();

			config.jetty.modifyHttpConfiguration(jettyconf -> {
				jettyconf.setIdleTimeout(60 * 10 * 1000);
			});

			config.bundledPlugins.enableCors(cors -> {

				cors.addRule(it -> {
					it.anyHost();
				});
			});
			config.router.apiBuilder(() -> {
				OpenWareInstance.getInstance()
								.logTrace(
										"[PlUGINS] " + "------------------Plugins loading...-------------------------");
				// Plugins
				loadPlugins();
				OpenWareInstance.getInstance()
								.logTrace(
										"[PLUGINS] " + "------------------Plugins loaded...-------------------------");

				path("api", () -> {
					for (OpenWareAPI os : services) {
						os.registerRoutes();
					}
				});
				ws(LIVE_API, ws -> {
					JavalinWebsocketProvider jwp = new JavalinWebsocketProvider();
					jwp.registerWSforJavalin(ws);
				});
				after(ctx -> {
					Long started = ctx.sessionAttribute("request_started");
					if (started != null) {
						long ended = System.currentTimeMillis();
						long duration = ended - started;
						accessLogger.debug("[API-ACCESS][SOURCE:" + ctx.host() + "][" + ctx.method() + "]" + ctx.path()
								+ "handled in " + duration + "ms");
					}

				});
				before("/api/*", ctx -> {
					accessLogger.debug("[API-ACCESS][SOURCE:" + ctx.host() + "][" + ctx.method() + "]" + ctx.path());

					// request.session(true);
					if (Config.getBool("accessControl", true)) {
						String method = ctx	.method()
											.toString()
											.toLowerCase();
						if (!method.equals("options")) {
							boolean authorized = false;
							User user;
							if (ctx	.queryParamMap()
									.keySet()
									.contains("username")
									&& ctx	.queryParamMap()
											.keySet()
											.contains("password")) {
								user = UserService	.getInstance()
													.login(ctx.queryParam("username"), ctx.queryParam("password"));
							} else {
								Map<String, String> header = ctx.headerMap();

								user = UserService	.getInstance()
													.checkAuth(ctx.header(UserAPI.OD_SESSION));
							}

							if (ctx	.headerMap()
									.keySet()
									.contains("Authorization")
									&& ctx	.header("Authorization")
											.startsWith("Bearer ")) {
								user = UserService	.getInstance()
													.jwtToUser(ctx	.header("Authorization")
																	.substring(7));
								ctx.sessionAttribute("apiaccess", true);
							}
							authorized = user != null;

							if (!authorized) {
								throw new ForbiddenResponse("Not authorized");
							}
							ctx.sessionAttribute("user", user);
							ctx.sessionAttribute("request_started", System.currentTimeMillis());

						}

					}
				});

			});

		});
		javalinInstance.exception(Exception.class, (e, ctx) -> {
			JSONObject details = new JSONObject();

			JSONObject params = new JSONObject();
			for (String key : ctx	.pathParamMap()
									.keySet()) {
				params.put(key, ctx.pathParam(key));
			}
			details.put("pathParams", params);

			JSONObject queryP = new JSONObject();
			for (String key : ctx	.queryParamMap()
									.keySet()) {
				queryP.put(key, ctx.queryParam(key));
			}
			User user = (User) ctx.sessionAttribute("user");
			JSONObject uInfo = new JSONObject();
			if (user != null) {
				uInfo = user.toJSON();
			}
			details.put("queryParams", queryP);
			details.put("url", ctx.fullUrl());
			details.put("user", uInfo);

			OpenWareInstance.getInstance()
							.logError("Error on " + ctx.fullUrl() + ": " + e.getLocalizedMessage() + "\n"
									+ details.toString(2), e);
			// HTTPResponseHelper.generateResponse(ctx, 400, null, e.getMessage());
		});

		logInfo("INSTANCE CREATED");

	}

	private void loadAnalyticSensorProvider() {
		ServiceLoader<AnalyticSensorProvider> loader = ServiceLoader.load(AnalyticSensorProvider.class);
		try {
			Iterator<AnalyticSensorProvider> iterator = loader.iterator();

			while (iterator.hasNext()) {
				AnalyticSensorProvider provider = iterator.next();
				OWService aHandler = new OWService(provider	.getClass()
															.getCanonicalName(),
						provider, new OWServiceActivator() {

							@Override
							public boolean unload() throws Exception {
								// TODO:AnalyticsService.getInstance().remove(provider)
								return false;
							}

							@Override
							public Object load(Object prevInstance, JSONObject options) throws Exception {
								if (prevInstance != null) {
									// TODO:AnalyticsService.getInstance().remove(provider)
								}
								provider.init(options);
								AnalyticsService.getInstance()
												.addSensorProvider(provider);

								logInfo(provider.getClass()
												.getCanonicalName()
										+ " loaded!");
								return provider;
							}
						});
				if (!aHandler.isDeactivated()) {
					try {
						aHandler.load(null);
					} catch (Exception e) {
						logError("Could not load DataHandler " + aHandler	.getClass()
																			.getCanonicalName(),
								e);
					}
				}

			}

		} catch (NoSuchElementException e) {
			logInfo("No Analytic Providers Loaded!");
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
			OpenWareInstance.getInstance()
							.logError(
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

	private void startStatsMonitoring() {
		Timer t = new Timer();

		t.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				List<OpenWareValueDimension> dims = new ArrayList<OpenWareValueDimension>();
				dims.add(new OpenWareNumber("MinuteUTC", "min", 0d));
				dims.add(new OpenWareNumber("MinuteOfDay", "min", 0d));
				dims.add(new OpenWareNumber("HourUTC", "h", 0d));
				dims.add(new OpenWareNumber("IsoWeekday", "", 0d));
				dims.add(new OpenWareNumber("IsoWeek", "", 0d));
				dims.add(new OpenWareNumber("DayOfMonth", "", 0d));
				dims.add(new OpenWareNumber("Month", "", 0d));
				dims.add(new OpenWareNumber("DayOfYear", "", 0d));
				dims.add(new OpenWareNumber("Year", "", 0d));
				dims.add(new OpenWareNumber("Timestamp", "ms", 0d));
				OpenWareDataItem heartbeat = new OpenWareDataItem("heartbeat", "_owinternal", "Heartbeat",
						new JSONObject(), dims);

				long current = DataTools.floorDate(System.currentTimeMillis(), 60000);

				Instant i = Instant.ofEpochMilli(current);
				LocalDateTime ldt = LocalDateTime.ofInstant(i, ZoneOffset.UTC);
				int min = ldt.getMinute();
				int hour = ldt.getHour();
				int minofDay = hour * 60 + min;
				int weekday = ldt	.getDayOfWeek()
									.getValue();
				int week = ldt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
				int dayofmonth = ldt.getDayOfMonth();
				int month = ldt.getMonthValue();
				int dayofyear = ldt.getDayOfYear();
				int year = ldt.getYear();
				OpenWareValue vals = new OpenWareValue(current);
				try {
					vals.add(dims	.get(0)
									.createValueForDimension(min));
					vals.add(dims	.get(1)
									.createValueForDimension(minofDay));
					vals.add(dims	.get(2)
									.createValueForDimension(hour));
					vals.add(dims	.get(3)
									.createValueForDimension(weekday));
					vals.add(dims	.get(4)
									.createValueForDimension(week));
					vals.add(dims	.get(5)
									.createValueForDimension(dayofmonth));
					vals.add(dims	.get(6)
									.createValueForDimension(month));
					vals.add(dims	.get(7)
									.createValueForDimension(dayofyear));
					vals.add(dims	.get(8)
									.createValueForDimension(year));
					vals.add(dims	.get(9)
									.createValueForDimension(current));
					heartbeat.value(Lists.asList(vals, new OpenWareValue[0]));
					DataService.onNewData(heartbeat);
					logInfo("[Heartbeat] " + i.toString());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}, 0, 60000);
	}

	public void startInstance() {

		if (!isRunning()) {
			this.started.complete(true);
			javalinInstance.start(Config.getInt("sparkPort", 4567));
			OpenWareInstance.getInstance()
							.logInfo("Started WebServer...");
			getInstance()	.awaitInitialization()
							.whenComplete((success, error) -> {
								startStatsMonitoring();
							});
		}
		setRunning(true);
	}

	public void stopInstance() {
		if (isRunning()) {
			javalinInstance.stop();
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
			OWService aHandler = new OWService(handler	.getClass()
														.getCanonicalName(),
					handler, new OWServiceActivator() {

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
							logInfo(handler	.getClass()
											.getCanonicalName()
									+ " loaded!");
							return handler;
						}
					});
			if (!aHandler.isDeactivated()) {
				try {
					aHandler.load(null);
				} catch (Exception e) {
					logError("Could not load DataHandler " + aHandler	.getClass()
																		.getCanonicalName(),
							e);
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
			OWService anOperation = new OWService(op.getClass()
													.getCanonicalName(),
					op, new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							TransformationService	.getInstance()
													.removeOperation(op.getClass());
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							if (prevInstance != null) {
								TransformationService	.getInstance()
														.removeOperation(op.getClass());
							}
							TransformationService	.getInstance()
													.registerOperation(op.getClass());
							logInfo(op	.getClass()
										.getCanonicalName()
									+ " loaded!");
							return op;
						}
					});
			if (!anOperation.isDeactivated()) {
				try {
					anOperation.load(null);
				} catch (Exception e) {
					logError("Could not load TransformationOperation " + anOperation.getClass()
																					.getCanonicalName(),
							e);
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
			OWService anActuator = new OWService(actor	.getClass()
														.getCanonicalName(),
					actor, new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							DataService.remove(actor);
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							actor.init(options, true);
							DataService.addActuator(actor);
							logInfo(actor	.getClass()
											.getCanonicalName()
									+ " loaded!");
							return actor;
						}
					});
			if (!anActuator.isDeactivated()) {
				try {
					anActuator.load(null);
				} catch (Exception e) {
					logError("Could not load Actuator " + anActuator.getClass()
																	.getCanonicalName(),
							e);
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
			OWService aProvider = new OWService(provider.getClass()
														.getCanonicalName(),
					provider, new OWServiceActivator() {

						@Override
						public boolean unload() throws Exception {
							ReportsService	.getInstance()
											.removeReportType(provider.getTag());
							return true;
						}

						@Override
						public Object load(Object prevInstance, JSONObject options) throws Exception {
							if (prevInstance != null) {
								ReportsService	.getInstance()
												.removeReportType(provider.getTag());
							}
							ReportsService	.getInstance()
											.addReportType(provider.getTag(),
													(Class<ReportInterface>) provider.getClass());
							logInfo(provider.getClass()
											.getCanonicalName()
									+ " loaded!");
							return provider;
						}
					});
			if (!aProvider.isDeactivated()) {
				try {
					aProvider.load(null);
				} catch (Exception e) {
					logError("Could not load ReportType " + aProvider	.getClass()
																		.getCanonicalName(),
							e);
				}
			}

		}
	}

	/*
	 * // V-Sensor private void loadAnalyticsProvider() {
	 * logInfo("------- 			Loading Analytic Providers			------");
	 * ServiceLoader<AnalyticsProvider> loader =
	 * ServiceLoader.load(AnalyticsProvider.class); Iterator<AnalyticsProvider> it =
	 * loader.iterator(); while (it.hasNext()) { AnalyticsProvider provider =
	 * it.next(); OWService aProviderService = new
	 * OWService(provider.getClass().getCanonicalName(), provider, new
	 * OWServiceActivator() {
	 * 
	 * @Override public boolean unload() throws Exception {
	 * AnalyticsService.getInstance().deregisterAnalyticsProvider(provider.getOID())
	 * ; logInfo(provider.getClass().getCanonicalName() + " loaded!"); return true;
	 * }
	 * 
	 * @Override public Object load(Object prevInstance, JSONObject options) throws
	 * Exception {
	 * AnalyticsService.getInstance().registerAnalyticsProvider(provider.getOID(),
	 * provider); logInfo(provider.getClass().getCanonicalName() + " loaded!");
	 * return provider; } }); if (!aProviderService.isDeactivated()) { try {
	 * aProviderService.load(null); } catch (Exception e) {
	 * logError("Could not load AnalyticsProvider " +
	 * aProviderService.getClass().getCanonicalName(), e); } } } }
	 */
	private void loadHTTPAPI() {
		logInfo("------- 				Loading HTTP APIs 				------");
		ServiceLoader<OpenWareAPI> loader = ServiceLoader.load(OpenWareAPI.class);
		Iterator<OpenWareAPI> it = loader.iterator();
		while (it.hasNext()) {
			OpenWareAPI api = it.next();
			registerService(api);
			logInfo(api	.getClass()
						.getCanonicalName()
					+ " loaded!");
		}
	}

	private UserAdapter loadUserAdapter() throws Exception {
		logInfo("------- 				Loading User Adapter			------");
		ServiceLoader<UserAdapter> loader = ServiceLoader.load(UserAdapter.class);
		UserAdapter adapter = loader.iterator()
									.next();
		logInfo(adapter	.getClass()
						.getCanonicalName()
				+ " loaded!");
		return adapter;

	}

	private PersistenceAdapter loadPersistenceAdapter() {
		logInfo("------- 			Loading Persistence Adapter			------");
		ServiceLoader<PersistenceAdapter> loader = ServiceLoader.load(PersistenceAdapter.class);
		try {
			PersistenceAdapter adapter = loader	.iterator()
												.next();
			logInfo(adapter	.getClass()
							.getCanonicalName()
					+ " loaded!");
			return adapter;
		} catch (NoSuchElementException e) {
			return null;
		}

	}

	private ReferenceAdapter loadReferenceAdapterAdapter() {
		logInfo("------- 			Loading Reference Adapter			------");
		ServiceLoader<ReferenceAdapter> loader = ServiceLoader.load(ReferenceAdapter.class);
		try {
			ReferenceAdapter adapter = loader	.iterator()
												.next();
			logInfo(adapter	.getClass()
							.getCanonicalName()
					+ " loaded!");
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
				OWService pluginService = new OWService(plugin	.getClass()
																.getCanonicalName(),
						plugin, new OWServiceActivator() {

							@Override
							public boolean unload() throws Exception {
								plugin.disable();
								return true;
							}

							@Override
							public Object load(Object prevInstance, JSONObject options) throws Exception {
								plugin.init(OpenWareInstance.getInstance(), options);
								logInfo(plugin	.getClass()
												.getCanonicalName()
										+ " loaded!");
								return plugin;
							}
						});

				if (!pluginService.isDeactivated())
					pluginService.load(null);
			} catch (Exception e) {
				logError("Error while loading Plugin " + plugin	.getClass()
																.getCanonicalName(),
						e);
			}
		}
	}

	public ScheduledExecutorService getCommonExecuteService() {
		return commonExecuteService;
	}

	public Gson getGSONInstance() {
		return gson;
	}
}

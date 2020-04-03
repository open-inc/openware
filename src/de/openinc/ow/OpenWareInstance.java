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
import java.util.TimeZone;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.helper.HTTPResponseHelper;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.http.SubscriptionProvider;
import de.openinc.ow.http.UserAPI;
import de.openinc.ow.middleware.services.UserService;

public class OpenWareInstance {
	/**
	 * API constant to setup a websocket subscription
	 */
	public static final String LIVE_API = "/subscription";

	public String VERSION = "1.00";

	protected Logger logger;

	static Logger debugLogger;
	static Logger infoLogger;
	static Logger errorLogger;
	static Logger mqttLogger;

	static Logger apacheLogger;
	static Logger sparkLogger;
	static Logger jettyLogger;
	static Logger mongoLogger;
	static Logger xdocLogger;

	private static OpenWareInstance me;
	private static ArrayList<OpenWareAPI> services;
	private static boolean running = false;

	public void logInfo(Object info) {

		this.debugLogger.info(info);

	}

	public void logMQTT(Object info) {

		this.mqttLogger.info(info);

	}

	public void logDebug(Object debug) {

		this.debugLogger.debug(debug);

	}

	public void logDebug(Object debug, Throwable t) {

		debugLogger.debug(debug, t);

	}

	public void logTrace(Object trace) {

		debugLogger.trace(trace);

	}

	public void logWarn(Object warn) {

		debugLogger.warn(warn);

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
		this.infoLogger = LogManager.getLogger(OpenWareInstance.class);
		this.debugLogger = LogManager.getLogger("requestLogger");
		this.errorLogger = LogManager.getLogger("errorLogger");
		this.mqttLogger = LogManager.getLogger("mqttLogger");

		this.apacheLogger = LogManager.getLogger("org.apache.http");
		this.sparkLogger = LogManager.getLogger("spark");
		this.jettyLogger = LogManager.getLogger("org.eclipse.jetty");
		this.mongoLogger = LogManager.getLogger("org.mongodb.driver");
		this.xdocLogger = LogManager.getLogger("fr.opensagres.xdocreport");

		this.me = this;
		this.services = new ArrayList<OpenWareAPI>();

		Config.init();

		switch (Config.logLevel) {
		case "TRACE":
			infoLogger.setLevel(Level.TRACE);
			break;
		case "DEBUG":
			infoLogger.setLevel(Level.DEBUG);
			break;
		case "OFF":
			infoLogger.setLevel(Level.OFF);
			break;
		case "INFO":
			infoLogger.setLevel(Level.INFO);
			break;
		case "ERROR":
			infoLogger.setLevel(Level.ERROR);
			break;
		case "WARN":
			infoLogger.setLevel(Level.WARN);
			break;
		case "ALL":
			infoLogger.setLevel(Level.ALL);
			break;
		default:
			infoLogger.setLevel(Level.INFO);
			break;
		}

		switch (Config.logLevelAPIs) {
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

		TimeZone zone = TimeZone.getDefault();
		//System.out.println(zone.getDisplayName());
		//System.out.println(zone.getID());
		//System.out.println(Config.timezone);
		TimeZone.setDefault(TimeZone.getTimeZone(Config.timezone));
		zone = TimeZone.getDefault();
		//System.out.println(zone.getDisplayName());
		//System.out.println(zone.getID());
		if (Config.enableWebserver) {
			logInfo("Initializing open.WARE v" + OpenWareInstance.getInstance().VERSION +
					" on port " +
					Config.sparkPort);
			port(Integer.valueOf(Config.sparkPort));

			if (Boolean.valueOf(Config.sparkSSL)) {
				OpenWareInstance.getInstance().logInfo(
						"Setting up encryption. Warning: keystore file expires and has to be manually replaced.");
				secure(Config.keystoreFilePath, Config.keystorePassword, null, null);
			}
		}

	}

	public static OpenWareInstance getInstance() {
		if (me == null) {
			me = new OpenWareInstance();
		}
		return me;
	}

	public static void registerService(OpenWareAPI service) {
		services.add(service);

		if (isRunning()) {
			OpenWareInstance.getInstance().logError(
					"Server already running! You need to restart the Instance to access newly registered Services");
		}
	}

	public void startInstance() {
		if (!isRunning()) {

			OpenWareInstance.getInstance().logInfo("Using external file path: " + Config.sparkFileDir);
			externalStaticFileLocation(Config.sparkFileDir); // index.html is served at localhost:4567 (default port)
			webSocket(LIVE_API, SubscriptionProvider.class);

			after((req, res) -> {
				if (req.url().contains("/api/transform")) {
					System.out.println(
							"-------------------------------------------Freeing Memory-----------------------------------------------------");
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

				OpenWareInstance.getInstance().logError("Error on " + req.url() +
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
				if (Config.accessControl) {
					boolean authorized = false;

					User user = UserService.getInstance().checkAuth(request.headers(UserAPI.OD_SESSION));
					authorized = user != null;
					request.session().attribute("user", user);

					// if (!Config.whiteList.contains(request.ip()) ) {
					// OpenWareInstance.getInstance().logError("Access control denied request from
					// ip address " + request.ip() + " - not on whitelist.");
					// halt(401, "Not authorized");
					// }

					if (!authorized) {
						halt(401, "Not authorized");
					}
				}
			}

			);
			String index;
			try {
				index = new String(Files.readAllBytes(Paths.get(Config.sparkFileDir + "/index.html")));
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

			init();

		}

		if (Boolean.valueOf(Config.useUsernames)) {
			OpenWareInstance.getInstance()
					.logInfo("Server is set to authenticated, API calls will be filtered by username.");
		} else {
			OpenWareInstance.getInstance()
					.logInfo("Authentification is turned off, API calls will ignore user names internally.");
		}

		if (Config.accessControl) {
			OpenWareInstance.getInstance().logInfo(
					"Access control activated, only whitelisted IP addresses will be allowed to access Rest API.");
		} else {
			OpenWareInstance.getInstance().logInfo("Access control for Rest API not active.");
		}

		setRunning(true);
	}

	public void stopInstance() {
		if (isRunning()) {
			stop();
		}
		setRunning(false);
	}

	public static boolean isRunning() {
		return running;
	}

	public static void setRunning(boolean running) {
		OpenWareInstance.running = running;
	}

}
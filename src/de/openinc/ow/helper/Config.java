package de.openinc.ow.helper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import io.github.cdimascio.dotenv.Dotenv;

public class Config {

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
			String envConf = env.get(
					env.get("OW_ENV_PREFIX", "OW_") + configName.replace(".", "_").toUpperCase());
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

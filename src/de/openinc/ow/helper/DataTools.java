package de.openinc.ow.helper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import com.google.common.base.CharMatcher;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;

public class DataTools {
	private static CharMatcher isoMatcher = CharMatcher.javaIsoControl();
	public static final int DATE_QUARTER_HELPER_CHRONO = -3465345;

	public static long floorDate(long dateInMillis) {
		return floorDate(dateInMillis, Config.getLong("baseTimeInterval", (60l * 60l * 1000l)));
	}

	public static long floorDate(long dateInMillis, long interval) {
		if (interval == 0) {
			return dateInMillis;
		}
		long temp = dateInMillis % interval;
		return (dateInMillis - temp);
	}

	public static Object listGetOrDefault(List<Object> list, int index, Object defaultVal) {

		if (list.size() <= index || list.get(index) == null) {
			return defaultVal;
		}
		return list.get(index);
	}

	public static List<long[]> generateTimeIntervals(long startTS, long endTS, String unitString) {
		List<long[]> intervals = new ArrayList<>();
		Integer unit = null;

		if (unitString.equals("second")) {
			unit = Calendar.SECOND;
		}
		if (unitString.equals("hour")) {
			unit = Calendar.HOUR;
		}
		if (unitString.equals("minute")) {
			unit = Calendar.MINUTE;
		}
		if (unitString.equals("day")) {
			unit = Calendar.DATE;
		}
		if (unitString.equals("week")) {
			unit = Calendar.WEEK_OF_YEAR;
		}
		if (unitString.equals("month")) {
			unit = Calendar.MONTH;
		}
		if (unitString.equals("quarter")) {
			unit = DATE_QUARTER_HELPER_CHRONO;
		}
		if (unitString.equals("year")) {
			unit = Calendar.YEAR;
		}
		if (unit == null) {
			throw new IllegalArgumentException(
					"Unit needs to be one of  'second | hour | minute | day | week | month | year'");
		}

		Date start = new Date(startTS);
		Calendar current = Calendar.getInstance();
		current.setTime(start);
		long[] initial = getStartEndOfDateUnit(startTS, unit);
		intervals.add(new long[] { startTS, Math.min(initial[1], endTS) });

		while (current.getTimeInMillis() < endTS) {
			if (unit == DATE_QUARTER_HELPER_CHRONO) {
				current.add(Calendar.MONTH, 3);
			} else {
				current.add(unit, 1);
			}

			long currentTS = current.getTimeInMillis();
			if (currentTS > endTS)
				break;
			long[] interval = getStartEndOfDateUnit(currentTS, unit);
			interval[1] = Math.min(endTS, interval[1]);
			intervals.add(interval);
		}

		return intervals;
	}

	public static long[] getStartEndOfDateUnit(long date, String unitString) {
		Integer unit = null;
		if (unitString.equals("second")) {
			unit = Calendar.SECOND;
		}
		if (unitString.equals("hour")) {
			unit = Calendar.HOUR;
		}
		if (unitString.equals("minute")) {
			unit = Calendar.MINUTE;
		}
		if (unitString.equals("day")) {
			unit = Calendar.DATE;
		}
		if (unitString.equals("week")) {
			unit = Calendar.WEEK_OF_YEAR;
		}
		if (unitString.equals("month")) {
			unit = Calendar.MONTH;
		}
		if (unitString.equals("quarter")) {
			unit = DATE_QUARTER_HELPER_CHRONO;
		}
		if (unitString.equals("year")) {
			unit = Calendar.YEAR;
		}
		if (unit == null) {
			throw new IllegalArgumentException(
					"Unit needs to be one of  'second | hour | minute | day | week | month | quarter | year'");
		}
		return getStartEndOfDateUnit(date, unit);
	}

	public static long[] getStartEndOfDateUnit(long date, int unit) {

		switch (unit) {
		case Calendar.SECOND: {
			long start = date - (date % 1000);
			long end = start + 999;
			return new long[] { start, end };
		}
		case Calendar.MINUTE: {
			long start = date - (date % (60l * 1000l));
			long end = start + (60l * 1000l) - 1;
			return new long[] { start, end };
		}
		case Calendar.HOUR: {
			long start = date - (date % (60l * 60l * 1000l));
			long end = start + (60l * 60l * 1000l) - 1;
			return new long[] { start, end };
		}
		case Calendar.DATE: {
			long start = date - (date % (24l * 60l * 60l * 1000l));
			long end = start + (24l * 60l * 60l * 1000l) - 1;
			return new long[] { start, end };
		}
		case Calendar.WEEK_OF_YEAR: {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(date);
			cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long start = cal.getTimeInMillis();
			long end = start - 1 + (7l * 24l * 60l * 60l * 1000l);
			return new long[] { start, end };
		}
		case Calendar.MONTH: {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(date);
			cal.set(Calendar.DATE, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long start = cal.getTimeInMillis();
			cal.add(Calendar.MONTH, 1);
			long end = cal.getTimeInMillis() - 1;
			return new long[] { start, end };
		}
		case DATE_QUARTER_HELPER_CHRONO: {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(date);
			int currentMonth = cal.get(Calendar.MONTH);
			int quarterMonth = currentMonth - (currentMonth % 3);
			cal.set(Calendar.MONTH, quarterMonth);
			cal.set(Calendar.DATE, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long start = cal.getTimeInMillis();
			cal.add(Calendar.YEAR, 1);
			long end = cal.getTimeInMillis() - 1;
			return new long[] { start, end };
		}
		case Calendar.YEAR: {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(date);
			cal.set(Calendar.MONTH, Calendar.JANUARY);
			cal.set(Calendar.DATE, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long start = cal.getTimeInMillis();
			cal.add(Calendar.YEAR, 1);
			long end = cal.getTimeInMillis() - 1;
			return new long[] { start, end };
		}
		default:
			throw new IllegalArgumentException("Wrong time unit");
		}

	}

	public static String mapSPSDataType(String type) {
		HashMap<String, String> mapping = new HashMap<>();
		mapping.put("WORD", "number");
		mapping.put("BYTE", "number");
		mapping.put("DWORD", "number");
		mapping.put("LWORD", "number");
		mapping.put("SINT", "number");
		mapping.put("USINT", "number");
		mapping.put("INT", "number");
		mapping.put("UINT", "number");
		mapping.put("DINT", "number");
		mapping.put("UDINT", "number");
		mapping.put("LINT", "number");
		mapping.put("ULINT", "number");
		mapping.put("REAL", "number");
		mapping.put("LREAL", "number");
		mapping.put("STRING", "string");
		mapping.put("WSTRING", "string");
		mapping.put("TIME", "number");
		mapping.put("TIME_OF_DAY", "number");
		mapping.put("DATE", "number");
		mapping.put("DATE_AND_TIME", "number");
		mapping.put("LTIME", "number");
		mapping.put("BOOL", "boolean");

		return mapping.get(type.toUpperCase());
	}

	public static String getJSONPartial(String key, Object value, boolean last, boolean isString) {
		if (isString) {
			value = "\"" + value + "\"";
		}
		return String.format("\"%s\" : %s", key, value) + (last ? "" : ",");
	}

	public static OpenWareDataItem getNoiseData(String id, String source, String name, int nrOfVals) {
		List<OpenWareValueDimension> valueTypes = new ArrayList<>();
		OpenWareNumber nr = new OpenWareNumber("tste", "kmh", 5.0);
		valueTypes.add(nr);

		OpenWareDataItem item = new OpenWareDataItem(id, source, name, new JSONObject(), valueTypes);

		long now = System.currentTimeMillis();
		long initialNow = now;
		List<OpenWareValue> vals = new ArrayList<OpenWareValue>();
		while ((initialNow + nrOfVals) > now) {
			OpenWareValue value = new OpenWareValue(now++);
			value.addValueDimension(nr);
			vals.add(value);
		}
		item.value(vals);
		return item;

	}

	public static String cleanAndValidate(String s) {
		return isoMatcher.removeFrom(s);
	}
}

package de.openinc.model.data;

import java.lang.reflect.Type;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class OpenWareDataItemSerializer implements JsonSerializer<OpenWareDataItem> {

	@Override
	public JsonElement serialize(OpenWareDataItem src, Type typeOfSrc, JsonSerializationContext context) {

		JsonObject obj = new JsonObject();

		obj.addProperty("id", src.getId());
		obj.addProperty("name", src.getName());
		obj.add("meta", JsonParser.parseString(src.getMeta().toString()).getAsJsonObject());
		obj.addProperty("source", src.getSource());
		obj.addProperty("user", src.getSource());
		if (src.getReference() != null) {
			obj.addProperty("reference", src.getReference());
		}
		JsonArray values = new JsonArray();
		JsonArray vtypes = new JsonArray();

		for (OpenWareValue val : src.value()) {
			JsonArray value = new JsonArray();
			for (OpenWareValueDimension dimen : val) {
				if (dimen.type().equals(OpenWareNumber.TYPE)) {
					value.add((double) dimen.value());
				}
				if (dimen.type().equals(OpenWareString.TYPE)) {
					value.add((String) dimen.value());
				}
				if (dimen.type().equals(OpenWareBoolValue.TYPE)) {
					value.add((boolean) dimen.value());
				}
				if (dimen.type().equals(OpenWareGeo.TYPE)) {
					value.add(JsonParser.parseString(((JSONObject) dimen.value()).toString()));
				}
				if (dimen.type().equals(OpenWareGeneric.TYPE)) {
					if (dimen.value() instanceof JSONObject) {
						value.add(JsonParser.parseString(((JSONObject) dimen.value()).toString()));
					} else {
						value.add(JsonParser.parseString(((JSONArray) dimen.value()).toString()));
					}

				}

			}
			JsonObject valueWithDate = new JsonObject();
			valueWithDate.addProperty("date", val.getDate());
			valueWithDate.add("value", value);
			values.add(valueWithDate);
		}
		for (int i = 0; i < src.getValueTypes().size(); i++) {
			List<OpenWareValueDimension> valueTypes = src.getValueTypes();
			JsonObject dimen = new JsonObject();
			dimen.addProperty("type", "Object");
			if (valueTypes.get(i) != null) {
				dimen.addProperty("type", valueTypes.get(i).type());
				dimen.addProperty("name", valueTypes.get(i).getName());
				dimen.addProperty("unit", valueTypes.get(i).getUnit());
			}

			vtypes.add(dimen);
		}

		obj.add("values", values);
		obj.add("valueTypes", vtypes);
		return obj;

	}

}

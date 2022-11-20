package de.openinc.model.data;

import static de.openinc.ow.helper.DataConversion.cleanAndValidate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.helper.DataConversion;;

/**
 * Basic data structure of the open.WARE Middleware
 * 
 * @author open.INC
 *
 */
public class OpenWareDataItem implements Comparable<OpenWareDataItem> {
	private List<OpenWareValue> values;
	private List<OpenWareValueDimension> valueTypes;
	private boolean persist;
	private String id;
	private String name;
	private JSONObject meta;
	private String source;
	private String reference;

	/**
	 * Can be used to generate OpenWareItems. Be Careful: Objects are *NOT*
	 * immutable. If an Item is recieved via an Service (e.g. DataService) best
	 * practice is to use clone() before altering Objects.
	 * 
	 * @param id         ID of the item. It needs to be unique within the source
	 * @param source     Source of the item. Can be used to group items. Combination
	 *                   of Source and ID need to be globally unique.
	 * @param name       Human-readable name of the item.
	 * @param meta       Custom data that will be available within all requests. Can
	 *                   be used to store data, e.g. for 3rd party apps.
	 * @param valueTypes Description of the different {@link OpenWareValueDimension}
	 */
	public OpenWareDataItem(String id, String source, String name, JSONObject meta,
			List<OpenWareValueDimension> valueTypes) {
		this.setId(id);
		this.setName(name);
		this.setMeta(meta);
		this.setSource(source);
		this.valueTypes = valueTypes;
		this.values = new ArrayList<OpenWareValue>();
		this.persist = true;
		this.reference = null;

	}

	public List<OpenWareValue> value() {
		return values;
	}

	public boolean equalsValueTypes(OpenWareDataItem other, boolean includeUnits) {
		if (this.valueTypes.size() != other.valueTypes.size())
			return false;
		int count = 0;
		for (OpenWareValueDimension dim : this.valueTypes) {
			OpenWareValueDimension otherDim = other.valueTypes.get(count++);
			if (!dim.type().equals(otherDim.type()))
				return false;
			if (includeUnits) {
				if (!dim.getUnit().equals(otherDim.getUnit()))
					return false;
			}
		}
		return true;
	}

	public void value(List<OpenWareValue> value) {
		Iterator<OpenWareValue> it = value.iterator();
		while (it.hasNext()) {
			OpenWareValue val = it.next();
			try {
				if (val != null && val.size() == this.getValueTypes().size()) {
					for (int i = 0; i < this.valueTypes.size(); i++) {
						if (!(val.get(i).type().equals(this.getValueTypes().get(i).type()))) {
							it.remove();
							break;
						}
					}
				} else {
					it.remove();
				}
			} catch (Exception e) {
				System.err.println("Here");
			}

		}
		if (value != null) {
			this.values = value;
		}
	}

	public boolean persist() {
		return persist;
	}

	public void setPersist(boolean persist) {
		this.meta.put("persist", persist);
		this.persist = persist;
	}

	public List<Integer> hasUnitDimension(String unit) {
		ArrayList<Integer> res = new ArrayList<>();

		for (int i = 0; i < valueTypes.size(); i++) {
			if (valueTypes.get(i).getUnit().toLowerCase().equals(unit.toLowerCase()))
				res.add(i);
		}
		return res;
	}

	/**
	 * Creates a copy of the Item. Members of the Item can be altered without
	 * altering the original Object (new reference)
	 * 
	 * @param includeValues Item can be cloned with or without values (<b>Warning:
	 *                      values will be assigned by reference!</b>)
	 * @return The copy of the original Item
	 */
	public OpenWareDataItem cloneItem(boolean includeValues) {
		ArrayList<OpenWareValueDimension> valueTypesNew = new ArrayList<>();
		for (OpenWareValueDimension dim : this.getValueTypes()) {
			valueTypesNew.add(dim.cloneDimension());
		}
		OpenWareDataItem item = new OpenWareDataItem(this.getId(), this.getSource(), this.getName(),
				new JSONObject(this.getMeta().toString()), valueTypesNew);

		item.setReference(this.reference);
		if (includeValues)
			item.value().addAll(this.value());
		item.setPersist(this.persist);

		return item;

	}

	/**
	 * Creates a copy of the Item. Members of the Item can be altered without
	 * altering the original Object (new reference). <b>Copy will include the values
	 * by reference!</b>
	 * 
	 * @return The copy of the original Item
	 */
	public OpenWareDataItem cloneItem() {
		return cloneItem(true);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public JSONObject getMeta() {
		return meta;
	}

	public void setMeta(JSONObject meta) {
		this.meta = meta;
	}

	@Override
	public int compareTo(OpenWareDataItem o) {
		return (this.getSource() + this.getId()).compareTo(o.getSource() + o.getId());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof OpenWareDataItem) {
			return ((OpenWareDataItem) obj).compareTo(this) == 0;
		}
		return false;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public static OpenWareDataItem fromJSON(JSONObject jobj) throws Exception {

		ArrayList<OpenWareValueDimension> valueTypes = new ArrayList<>();
		JSONArray vTypes = jobj.getJSONArray("valueTypes");
		for (int i = 0; i < vTypes.length(); i++) {
			valueTypes.add(OpenWareValueDimension.createNewDimension(
					cleanAndValidate(vTypes.getJSONObject(i).getString("name")),
					cleanAndValidate(vTypes.getJSONObject(i).optString("unit")),
					cleanAndValidate(vTypes.getJSONObject(i).getString("type"))));

		}

		if (jobj.has("annotations")) {
			jobj.getJSONObject("meta").put("annotations", jobj.get("annotations"));
		}
		String source;
		if (jobj.has("source")) {
			source = cleanAndValidate(jobj.getString("source"));
		} else {
			source = cleanAndValidate(jobj.getString("user"));
		}

		JSONObject meta;
		boolean persist = true;
		if (jobj.has("meta")) {
			meta = jobj.getJSONObject("meta");
			if (meta.has("persist")) {
				persist = meta.optBoolean("persist");
			}
		} else {
			meta = new JSONObject();
		}
		OpenWareDataItem item = new OpenWareDataItem(cleanAndValidate(jobj.getString("id")), source,
				cleanAndValidate(jobj.getString("name")), meta, valueTypes);
		item.setPersist(persist);
		if (jobj.has("reference")) {
			item.setReference(cleanAndValidate(jobj.getString("reference")));
		}

		ArrayList<OpenWareValue> owvalues = new ArrayList<>();
		JSONArray values = jobj.optJSONArray("values");
		if (values != null) {
			for (int i = 0; i < values.length(); i++) {
				JSONObject date = values.getJSONObject(i).optJSONObject("date");
				long dateVal;
				if (date != null) {
					dateVal = date.getLong("$numberLong");
				} else {
					dateVal = values.getJSONObject(i).getLong("date");
				}
				OpenWareValue currentV = new OpenWareValue(dateVal);

				JSONArray currentVValues = values.getJSONObject(i).getJSONArray("value");
				for (int j = 0; j < currentVValues.length(); j++) {
					currentV.addValueDimension(
							item.getValueTypes().get(j).createValueForDimension(currentVValues.get(j)));
				}
				owvalues.add(currentV);
			}
		}

		item.value(owvalues);
		return item;
	}

	public static OpenWareDataItem fromJSON(String data) throws Exception {

		JSONObject jobj = new JSONObject(data);
		return fromJSON(jobj);

	}

	public OpenWareValueDimension setValueTypeAtIndex(int index, OpenWareValueDimension dim) {
		return valueTypes.set(index, dim);
	}

	public OpenWareValueDimension removeValueTypeAtIndex(int index) {
		return valueTypes.remove(index);
	}

	public List<OpenWareValueDimension> getValueTypes() {
		return valueTypes;
	}

	public void setValueTypes(List<OpenWareValueDimension> valueTypes) {
		this.valueTypes = valueTypes;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	/**
	 * Will remove all dimension from **valueTypes** except the dimension with the
	 * specified index
	 * 
	 * @param dim The index of the dimension to keep
	 * @return The same {@link OpenWareDataItem} as before with altered valueTypes
	 * @throws IllegalArgumentException if the specified dimension is larger than
	 *                                  the valueTypes collection size
	 */
	public OpenWareDataItem reduceToSingleDimension(int dim) throws IllegalArgumentException {
		if (dim >= this.valueTypes.size()) {
			throw new IllegalArgumentException("Item has no Dimension " + dim);
		}
		List<OpenWareValueDimension> dim2keep = new ArrayList<OpenWareValueDimension>();
		dim2keep.add(this.valueTypes.get(dim));
		this.setValueTypes(dim2keep);
		return this;
	}

	/*
	 * public OpenWareValue getCurrentValueAt(long ts) { ArrayList<OpenWareValue>
	 * vals = new ArrayList<>(); vals.addAll(this.value()); vals.sort(new
	 * Comparator<OpenWareValue>() {
	 * 
	 * @Override public int compare(OpenWareValue o1, OpenWareValue o2) { // TODO
	 * Auto-generated method stub return 0; } });
	 * 
	 * int start = 0, end = vals.size();
	 * 
	 * int ans = -1; while (start <= end) { int mid = (start + end) / 2;
	 * 
	 * // Move to right side if target is // greater. if
	 * (item.value().get(mid).getDate() <= targetTS) { start = mid + 1; }
	 * 
	 * // Move left side. else { ans = mid; end = mid - 1; } } return ans;
	 * 
	 * }
	 */
	@Override
	public String toString() {
		StringBuffer res = new StringBuffer("{");
		res.append(DataConversion.getJSONPartial("id", StringEscapeUtils.escapeJava(this.id), false, true));
		res.append(DataConversion.getJSONPartial("name", this.name, false, true));
		res.append(DataConversion.getJSONPartial("meta", this.meta.toString(), false, false));
		res.append(DataConversion.getJSONPartial("user", this.source, false, true));
		res.append(DataConversion.getJSONPartial("source", this.source, false, true));
		if (reference != null) {
			res.append(DataConversion.getJSONPartial("reference", this.getReference(), false, true));
		}
		res.append("\"valueTypes\":");
		boolean first = true;
		res.append("[");
		for (OpenWareValueDimension val : this.valueTypes) {
			if (!first) {
				res.append(",");
			}
			res.append(val.toString());
			first = false;
		}
		res.append("],");
		res.append("\"values\": [");
		first = true;
		for (OpenWareValue val : this.values) {
			if (!first) {
				res.append(",");
			}
			res.append(val.toString());

			first = false;
		}
		res.append("]}");
		return res.toString();
	}

	public void streamPrint(OutputStream out) throws IOException {
		StringBuffer res = new StringBuffer("{");
		res.append(DataConversion.getJSONPartial("id", StringEscapeUtils.escapeJava(this.id), false, true));
		res.append(DataConversion.getJSONPartial("name", StringEscapeUtils.escapeJava(this.name), false, true));
		res.append(DataConversion.getJSONPartial("meta", this.meta.toString(), false, false));
		res.append(DataConversion.getJSONPartial("user", StringEscapeUtils.escapeJava(this.source), false, true));
		res.append(DataConversion.getJSONPartial("source", StringEscapeUtils.escapeJava(this.source), false, true));
		if (reference != null) {
			res.append(DataConversion.getJSONPartial("reference", StringEscapeUtils.escapeJava(this.getReference()),
					false, true));
		}
		res.append("\"valueTypes\":");
		boolean first = true;
		res.append("[");
		for (OpenWareValueDimension val : this.valueTypes) {

			if (!first) {
				res.append(",");
			}
			res.append(val.toString());

			first = false;
		}
		res.append("],");
		res.append("\"values\": [");
		out.write(res.toString().getBytes());

		first = true;
		for (OpenWareValue val : this.values) {

			if (!first) {
				out.write(",".getBytes());
			}
			out.write(val.toString().getBytes());
			first = false;
		}
		out.write("]}".getBytes());
		out.flush();
		out.close();

	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		obj.put("id", this.getId());
		obj.put("name", this.getName());
		obj.put("meta", this.getMeta());
		obj.put("source", this.getSource());
		obj.put("user", this.getSource());
		if (reference != null) {
			obj.put("reference", this.getReference());
		}
		JSONArray values = new JSONArray();
		JSONArray vtypes = new JSONArray();
		for (OpenWareValue val : value()) {
			JSONArray value = new JSONArray();
			for (OpenWareValueDimension dimen : val) {
				value.put(dimen.value());
			}
			JSONObject valueWithDate = new JSONObject();
			valueWithDate.put("date", val.getDate());
			valueWithDate.put("value", value);
			values.put(valueWithDate);
		}
		for (int i = 0; i < this.valueTypes.size(); i++) {
			JSONObject dimen = new JSONObject();
			dimen.put("type", "Object");
			if (valueTypes.get(i) != null) {
				dimen.put("type", valueTypes.get(i).type());
			}

			dimen.put("name", this.getName() + "Value " + i);
			if (valueTypes.get(i) != null) {
				dimen.put("name", valueTypes.get(i).getName());

			}

			dimen.put("unit", "");
			if (valueTypes.get(i) != null) {
				dimen.put("unit", valueTypes.get(i).getUnit());
			}

			vtypes.put(dimen);
		}

		obj.put("values", values);
		obj.put("valueTypes", vtypes);
		return obj;
	}

	/**
	 * Will compare the last value(s) of this item with the last value of
	 * {@code newValue}
	 * 
	 * @param newValue  The new {@link OpenWareDataItem} whose values will be
	 *                  compared to this item's values
	 * @param threshold A threshold in milliseconds which will additionally be
	 *                  checked to invalidate equality if {@code newValue} is much
	 *                  newer then this value
	 * @return true if values are equal or time between values is over threshold.
	 *         Otherwise false
	 */
	public boolean equalsLastValue(OpenWareDataItem newValue, long threshold) {
		if (this.valueTypes.size() == newValue.valueTypes.size()) {
			for (int i = 0; i < this.value().get(0).size(); i++) {
				boolean equal = this.value().get(0).get(i).value().equals(newValue.value().get(0).get(i).value());
				boolean toOld = (newValue.value().get(0).getDate() - this.value().get(0).getDate()) > threshold;
				if (!equal || toOld) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Will compare the last value(s) of this item with the last value of
	 * {@code newValue}
	 * 
	 * @param newValue The new {@link OpenWareDataItem} whose values will be
	 *                 compared to this items values
	 * @return true if values are equal otherwise false
	 */
	public boolean equalsLastValue(OpenWareDataItem newValue) {
		return equalsLastValue(newValue, Long.MAX_VALUE);
	}

	public OpenWareValueDimension newValueForDimension(int dim, Object value) throws Exception {
		return this.getValueTypes().get(dim).createValueForDimension(value);

	}

}

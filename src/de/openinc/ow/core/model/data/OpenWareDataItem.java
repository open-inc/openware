package de.openinc.ow.core.model.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.DataConversion;

/**
 * @author marti
 *
 */
public class OpenWareDataItem implements Comparable<OpenWareDataItem> {
	private List<OpenWareValue> values;
	private List<OpenWareValueDimension> valueTypes;
	private boolean persist;
	private String id;
	private String name;
	private JSONObject meta;
	private String user;
	private String reference;

	/**
	 * Can be used to generate OpenWareItems. Be Careful: Objects are *NOT*
	 * immutable. If an Item is recieved via an Service (e.g. DataService) best
	 * practice is to use clone() before altering Objects.
	 * 
	 * @param id
	 *            ID of the item. It needs to be unique within the source
	 * @param source
	 *            Source of the item. Can be used to group items. Combination of
	 *            Source and ID need to be globally unique.
	 * @param name
	 *            Human-readable name of the item.
	 * @param meta
	 *            Custom data that will be available within all requests. Can be
	 *            used to store data, e.g. for 3rd party apps.
	 * @param valueTypes
	 *            Description of the different {@link OpenWareValueDimension}
	 */
	public OpenWareDataItem(String id, String source, String name, JSONObject meta,
			List<OpenWareValueDimension> valueTypes) {
		this.setId(id);
		this.setName(name);
		this.setMeta(meta);
		this.setUser(source);
		this.valueTypes = valueTypes;
		this.values = new ArrayList<OpenWareValue>();
		this.persist = true;
		this.reference = null;
	}

	public List<OpenWareValue> value() {
		return values;
	}

	public void value(List<OpenWareValue> value) {
		Iterator<OpenWareValue> it = value.iterator();
		while (it.hasNext()) {
			OpenWareValue val = it.next();
			if (val.size() == this.getValueTypes().size()) {
				for (int i = 0; i < this.valueTypes.size(); i++) {
					if (!(val.get(i).type().equals(this.getValueTypes().get(i).type()))) {
						it.remove();
						break;
					}
				}
			} else {
				it.remove();
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
	 * @param includeValues
	 *            Item can be cloned with or without values (<b>Warning: values will
	 *            be assigned by reference!</b>)
	 * @return The copy of the original Item
	 */
	public OpenWareDataItem cloneItem(boolean includeValues) {
		ArrayList<OpenWareValueDimension> valueTypesNew = new ArrayList<>();
		for (OpenWareValueDimension dim : this.getValueTypes()) {
			valueTypesNew.add(dim.cloneDimension());
		}
		OpenWareDataItem item = new OpenWareDataItem(this.getId(), this.getUser(), this.getName(),
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

	public int compareTo(OpenWareDataItem o) {
		return this.getId().compareTo(o.getId());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof OpenWareDataItem) {
			return ((OpenWareDataItem) obj).compareTo(this) == 0;
		}
		return false;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public static OpenWareDataItem fromJSON(JSONObject jobj) {
		try {
			ArrayList<OpenWareValueDimension> valueTypes = new ArrayList<>();
			JSONArray vTypes = jobj.getJSONArray("valueTypes");
			for (int i = 0; i < vTypes.length(); i++) {
				valueTypes.add(OpenWareValueDimension.createNewDimension(vTypes.getJSONObject(i).getString("name"),
						vTypes.getJSONObject(i).optString("unit"), vTypes.getJSONObject(i).getString("type")));

			}
			if (jobj.has("annotations")) {
				jobj.getJSONObject("meta").put("annotations", jobj.get("annotations"));
			}

			OpenWareDataItem item = new OpenWareDataItem(jobj.getString("id"), jobj.getString("user"),
					jobj.getString("name"), jobj.getJSONObject("meta"), valueTypes);
			if (jobj.has("reference")) {
				item.setReference(jobj.getString("reference"));
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
		} catch (JSONException e) {
			OpenWareInstance.getInstance().logTrace("Error while Parsing JSON: " + e.getMessage() +
													"\n" +
													jobj.toString(2));
			return null;

		} catch (SecurityException e) {
			OpenWareInstance.getInstance().logError(e.getMessage(), e);
			return null;

		} catch (IllegalArgumentException e) {
			OpenWareInstance.getInstance().logError(e.getMessage(), e);
			return null;
		}
	}

	public static OpenWareDataItem fromJSON(String data) {
		try {
			JSONObject jobj = new JSONObject(data);
			return fromJSON(jobj);
		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError(e.getMessage(), e);
			return null;
		}

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
		public OpenWareValue getCurrentValueAt(long ts) {
			ArrayList<OpenWareValue> vals = new ArrayList<>();
			vals.addAll(this.value());
			vals.sort(new Comparator<OpenWareValue>() {
	
				@Override
				public int compare(OpenWareValue o1, OpenWareValue o2) {
					// TODO Auto-generated method stub
					return 0;
				}
			});
	
			int start = 0, end = vals.size();
	
			int ans = -1;
			while (start <= end) {
				int mid = (start + end) / 2;
	
				// Move to right side if target is  
				// greater.  
				if (item.value().get(mid).getDate() <= targetTS) {
					start = mid + 1;
				}
	
				// Move left side.  
				else {
					ans = mid;
					end = mid - 1;
				}
			}
			return ans;
	
		}
	*/
	@Override
	public String toString() {
		StringBuffer res = new StringBuffer("{");
		res.append(DataConversion.getJSONPartial("id", StringEscapeUtils.escapeJava(this.id), false, true));
		res.append(DataConversion.getJSONPartial("name", this.name, false, true));
		res.append(DataConversion.getJSONPartial("meta", this.meta.toString(), false, false));
		res.append(DataConversion.getJSONPartial("user", this.user, false, true));
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
		res.append(DataConversion.getJSONPartial("meta", StringEscapeUtils.escapeJava(this.meta.toString()), false,
				false));
		res.append(DataConversion.getJSONPartial("user", StringEscapeUtils.escapeJava(this.user), false, true));
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
		obj.put("user", this.getUser());
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

			dimen.put("name", this.getName() + "Value " +
								i);
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

}

package de.openinc.ow.core.model.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.DataHandler;
import de.openinc.ow.core.helper.Config;

public class OpenWareDataItem implements Comparable<OpenWareDataItem> {
	private List<OpenWareValue> values;
	private List<OpenWareValueDimension> valueTypes;

	private boolean persist;
	private static DataHandler jsonHandler = null;
	private String id;
	private String name;
	private JSONObject meta;
	private String user;
	private String reference;

	public OpenWareDataItem(String id, String user, String name, JSONObject meta,
			List<OpenWareValueDimension> valueTypes) {
		this.setId(id);
		this.setName(name);
		this.setMeta(meta);
		this.setUser(user);
		this.valueTypes = valueTypes;
		this.values = new ArrayList<OpenWareValue>();
		this.persist = true;
		this.reference = null;
	}

	public OpenWareDataItem(String id, String name, JSONObject meta, List<OpenWareValueDimension> valueTypes) {
		this(id, Config.standardUser, name, meta, valueTypes);
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

	@Override
	public String toString() {
		return toJSON().toString(4);
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
		JSONArray parents = new JSONArray();

		obj.put("parent", parents);
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

	public List<Integer> hasUnitDimension(String unit) {
		ArrayList<Integer> res = new ArrayList<>();

		for (int i = 0; i < valueTypes.size(); i++) {
			if (valueTypes.get(i).getUnit().toLowerCase().equals(unit.toLowerCase()))
				res.add(i);
		}
		return res;
	}

	public OpenWareDataItem cloneItem() {
		ArrayList<OpenWareValueDimension> valueTypesNew = new ArrayList<>();
		for (OpenWareValueDimension dim : this.getValueTypes()) {
			valueTypesNew.add(dim.cloneDimension());
		}
		OpenWareDataItem item = new OpenWareDataItem(this.getId(), this.getUser(), this.getName(),
				new JSONObject(this.getMeta().toString()), valueTypesNew);

		item.setReference(this.reference);
		item.value().addAll(this.value());
		item.setPersist(this.persist);

		return item;

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
			OpenWareInstance.getInstance().logError(e.getMessage(), e);
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

}

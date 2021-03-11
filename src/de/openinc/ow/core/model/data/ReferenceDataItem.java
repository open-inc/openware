package de.openinc.ow.core.model.data;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReferenceDataItem {

	private String reference;
	private List<OpenWareDataItem> items;

	public ReferenceDataItem(String ref) {
		this.reference = ref;
		items = new ArrayList<OpenWareDataItem>();
	}

	public boolean addItem(OpenWareDataItem item) {
		if (item != null && item.value().size() > 0) {
			return items.add(item);
		}
		return false;
	}

	public String getReferences() {
		return reference;
	}

	public JSONObject toJSON() {
		JSONObject res = new JSONObject();
		res.put("reference", reference);
		JSONArray jItems = new JSONArray();
		for (OpenWareDataItem item : items) {
			if (item != null) {
				jItems.put(item.toJSON());
			} else {
				jItems.put(new JSONObject());
			}
		}
		res.put("items", jItems);
		return res;
	}

	@Override
	public String toString() {
		return this.toJSON().toString(2);
	}

	public List<OpenWareDataItem> getItems() {
		return this.items;
	}

}

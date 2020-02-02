package de.openinc.ow.core.model.data;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReferenceDataItem {

	private String reference;
	private long start;
	private long end;
	private List<OpenWareDataItem> items;

	public ReferenceDataItem(String ref) {
		this.reference = ref;
		this.start = Long.MAX_VALUE;
		this.end = 0;
		items = new ArrayList<OpenWareDataItem>();
	}

	public boolean addItem(OpenWareDataItem item) {
		if (item != null && item.value().size() > 0) {
			this.start = Math.min(this.start, item.value().get(0).getDate());
			this.end = Math.max(this.end, item.value().get(item.value().size() - 1).getDate());
			return items.add(item);
		}
		return false;
	}

	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}

	public String getReferences() {
		return reference;
	}

	public JSONObject toJSON() {
		JSONObject res = new JSONObject();
		res.put("reference", reference);
		res.put("start", start);
		res.put("end", end);
		JSONArray jItems = new JSONArray();
		for (OpenWareDataItem item : items) {
			jItems.put(item.toJSON());
		}
		res.put("items", jItems);
		return res;
	}

	@Override
	public String toString() {
		return this.toJSON().toString(2);
	}

	public void setStart(long start) {
		this.start = start;
	}

	public void setEnd(long end) {
		this.end = end;
	}

	public List<OpenWareDataItem> getItems() {
		return this.items;
	}

}

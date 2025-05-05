package de.openinc.model.user;

import org.json.JSONArray;

public class PermissionSet {

	private String read;
	private String write;
	private String delete;

	public PermissionSet(String read, String write, String delete) {
		this.write = write;
		this.read = read;
		this.delete = delete;
	}

	public boolean evaluateRead(String sensor) {
		return sensor.matches(read);
	}

	public boolean evaluateWrite(String sensor) {
		return sensor.matches(write);
	}

	public boolean evaluateDelete(String sensor) {
		return sensor.matches(delete);
	}

	public String getRead() {
		return read;
	}

	public void setRead(String read) {
		this.read = read;
	}

	public String getWrite() {
		return write;
	}

	public void setWrite(String write) {
		this.write = write;
	}

	public String getDelete() {
		return delete;
	}

	public void setDelete(String delete) {
		this.delete = delete;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PermissionSet) {
			PermissionSet toCompare = (PermissionSet) obj;
			if (this.getDelete().equals(toCompare.getDelete()) && this.getWrite().equals(toCompare.getWrite())
					&& this.getRead().equals(toCompare.getRead())) {
				return true;
			}
		}
		return false;
	}

	public JSONArray toJSONArray() {
		JSONArray res = new JSONArray();
		res.put(read.toString());
		res.put(write.toString());
		res.put(delete.toString());
		return res;
	}

	@Override
	public String toString() {
		return toJSONArray().toString();
	}
}
package de.openinc.ow.monitoring;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.middleware.services.DataService;

public class Condition {
	private String type;
	private List<Condition> children;
	private User user;
	private Rule rule;
	private String source;
	private String id;
	private int dim;

	public Condition(String type, Rule rule, String source, String id, int dim) {
		this.type = type;
		this.children = null;
		this.rule = rule;
		this.source = source;
		this.id = id;
		this.dim = dim;

	}

	public Condition(String type, List<Condition> children, User user) {
		this.type = type;
		this.children = children;
		this.user = user;
	}

	public static Condition fromJSON(JSONObject o, User user) {

		if (o.has("trigger") || o.getString("type").equals("rule")) {
			String source = o.has("item_source") ? o.getString("item_source") : o.getString("source");
			String item_id = o.has("item_id") ? o.getString("item_id") : o.getString("id");
			int item_dim = o.has("item_dimension") ? o.getInt("item_dimension") : o.getInt("dimension");
			JSONObject rule = o.has("trigger") ? o.getJSONObject("trigger") : o.getJSONObject("rule");
			if (!user.canAccessRead(source, item_id))
				throw new IllegalAccessError("ConditionError: User is not allowed to read conditional value");
			return new Condition(o.getString("type"), Rule.fromJSON(rule), source, item_id, item_dim);
		} else {
			List<Condition> children = new ArrayList<Condition>();
			JSONArray jChildren = o.optJSONArray("children");
			for (int i = 0; i < jChildren.length(); i++) {
				children.add(Condition.fromJSON(jChildren.getJSONObject(i), user));
			}
			return new Condition(o.getString("type"), children, user);
		}
	}

	public static Condition complexCondition(String type, List<Condition> children, Rule rule, User user) {
		if (user == null)
			throw new IllegalArgumentException("User must not be null for conditional rules");
		return new Condition(type, children, user);
	}

	public boolean checkCondition(ConditionValueHolder refValue, ConditionValueHolder lastVal)
			throws IllegalAccessError {
		OpenWareDataItem currentVal;
		if (refValue.item.getId().equals(id) && refValue.item.getSource().equals(source)) {
			currentVal = refValue.item;
		} else {
			currentVal = DataService.getLiveSensorData(id, source);
		}
		if (type.equals("rule")) {
			return rule.check(currentVal, lastVal.item, refValue.item.value().get(0).get(refValue.dimension).value(),
					refValue.dimension);
		}
		if (type.equals("and")) {

			for (Condition child : children) {
				if (!child.checkCondition(refValue, lastVal))
					return false;
			}
			return true;
		}
		if (type.equals("or")) {

			for (Condition child : children) {
				if (child.checkCondition(refValue, lastVal))
					return true;
			}
			return false;
		}
		return false;
	}

	public String getType() {
		return type;
	}

	public List<Condition> getChildren() {
		return children;
	}

	public User getUser() {
		return user;
	}

	public static class ConditionValueHolder {
		public OpenWareDataItem item;
		public int dimension;

		public ConditionValueHolder(OpenWareDataItem item, int dim) {
			this.item = item;
			this.dimension = dim;
		}
	}

	public int getDim() {
		return dim;
	}
}

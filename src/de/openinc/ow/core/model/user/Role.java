package de.openinc.ow.core.model.user;

import java.util.ArrayList;

public class Role {

	String name;
	String id;
	ArrayList<String> children;
	ArrayList<String> user;
	
	public ArrayList<String> getUser() {
		return user;
	}


	public void setUser(ArrayList<String> user) {
		this.user = user;
	}


	public Role(String name, String id) {
		this.name = name;
		this.id = id;
		this.children = new ArrayList<>();
		this.user = new ArrayList<>();
	}
	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ArrayList<String> getChildren() {
		return children;
	}

	public void setChildren(ArrayList<String> children) {
		this.children = children;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Role) {
			return((Role) obj).getId().equals(this.getId());
		}
		return false;
	}
}

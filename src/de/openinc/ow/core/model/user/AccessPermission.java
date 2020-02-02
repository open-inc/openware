package de.openinc.ow.core.model.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AccessPermission {

	private HashMap<String, List<PermissionSet>> owners;
	
	public AccessPermission() {
		this.owners = new HashMap<>();
		
	}

	public void addOwner(String owner) {
		owners.put(owner, new ArrayList<PermissionSet>());
	}

	public void addPermission(String owner, PermissionSet permission) {
		List<PermissionSet> list = owners.getOrDefault(owner, new ArrayList<PermissionSet>());
		if(owners.containsKey(owner)&&list.contains(permission))return;
		list.add(permission);
		owners.put(owner,list);
	}
	
	public void changeOwnerOfPermission(String oldOwner, String newOWner) {
		List toChange = owners.get(oldOwner);
		owners.remove(oldOwner);
		owners.put(newOWner, toChange);
	}
	
	public void clearPermissions() {
		this.owners = new HashMap<>();
	}
	
	
	public void clearPermissionsForOwner(String owner) {
		this.owners.put(owner, new ArrayList<PermissionSet>());
	}
	
	public boolean evaluateRead(String owner, String sensor) {
		for(PermissionSet permission:owners.getOrDefault(owner, new ArrayList<>())) {
			if(permission.evaluateRead(sensor))return true;
		}
		return false;
	}
	public boolean evaluateWrite(String owner,String sensor) {
		for(PermissionSet permission:owners.getOrDefault(owner, new ArrayList<>())) {
			if(permission.evaluateWrite(sensor))return true;
		}
		return false;
	}
	public boolean evaluateDelete(String owner,String sensor) {
		for(PermissionSet permission:owners.getOrDefault(owner, new ArrayList<>())) {
			if(permission.evaluateDelete(sensor))return true;
		}
		return false;
	}
	
	public boolean serviceAccess(String owner, String type) {
		return owners.get(owner)!=null;
	}
	

}
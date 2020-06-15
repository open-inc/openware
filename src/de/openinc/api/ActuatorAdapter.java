package de.openinc.api;

public interface ActuatorAdapter {

	public String getID();

	public boolean send(String address, String target, String payload);

}

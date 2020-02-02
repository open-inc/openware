package de.openinc.ow.core.helper;

public class UsernameConverter {

	public static String getUsername(String username) {
		if(!Config.useUsernames){
			return Config.standardUser;
		}else {
			return username;
		}
	}
	
}

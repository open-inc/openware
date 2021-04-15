package de.openinc.ow.helper;

public class UsernameConverter {

	public static String getUsername(String username) {
		if(!Config.useUsernames){
			return Config.standardUser;
		}else {
			return username;
		}
	}
	
}

package de.openinc.ow.http;

import static spark.Spark.get;
import static spark.Spark.post;

import org.json.JSONObject;

import de.openinc.ow.core.api.OpenWareAPI;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;
import spark.Request;
import spark.Response;

public class UserAPI implements OpenWareAPI {
		
		private static final String OPERATION_ME = "me";
		private static final String OPERATION_DATA = "data";
		private static final String OPERATION_LOGIN = "login";
		private static final String OPERATION_SESSION_REFRESH ="refresh";
		public static final String OD_SESSION = "OD-SESSION";
		/**
		 * API constant to access User Management
		 */
		public static final String USER_API = "/users/:operation";
		public static final String USER_API_OBJECT = "/users/:operation/:oid";
				
		private Object handlePostData(Request request, Response response) {
			String header = request.headers(OD_SESSION);
			JSONObject parameter = new JSONObject(request.body());
			parameter.put(OD_SESSION, header);
			String op = request.params("operation");
			String key = request.params("oid");
			switch (op) {
				case OPERATION_DATA:{
					boolean success = UserService.getInstance().storeData(header, key, parameter.getString("value"));
					if(success) {
						response.status(200);
						return "Successfully saved value";
					}else {
						response.status(400);
						return "Could not save value";
					}
				}
				default:{
					response.status(400);
					return "Unkown User Operation";
				}
			}

		}
		
		private Object handleUserGet(Request request, Response response) {
			String op = request.params("operation");
			
			String header = request.headers(OD_SESSION);
			switch (op) {
				case OPERATION_ME:{
					User user = UserService.getInstance().checkAuth(header);
					if(user!=null) {
						response.status(200);
						response.type("application/json");
						return user.toString();
					}else {
						response.status(400);
						return "Invalid Session Token";
					}
				}
				case OPERATION_LOGIN:{
					String username = request.queryParams("username");
					String pw = request.queryParams("password");
					User user = UserService.getInstance().login(username, pw);
					if(user!=null) {
						response.status(200);
						response.type("application/json");
						return user.toString();
					}else {
						response.status(400);
						return "Invalid Credentials";
					} 
				}
				case OPERATION_DATA:{
					User user = UserService.getInstance().checkAuth(header);
					if(user!=null) {
						response.status(200);
						response.type("application/json");
						return user.getData();
					}else {
						response.status(400);
						return "Invalid Session Token";
					} 
				}
				case OPERATION_SESSION_REFRESH:{
					User user = UserService.getInstance().checkAuth(header);
					if(user!=null) {
						UserService.getInstance().refreshUsers();
						response.status(200);
						return "Triggered Refresh";
					}else {
						response.status(400);
						return "Invalid Session Token";
					}
				}
				default:{
					response.status(400);
					return "Unkown User Operation";
				}
			}
			
		}
		
		private Object handleSpecifcDataGet(Request request, Response response) {
			String op = request.params("operation");
			String key = request.params("oid");
			String header = request.headers(OD_SESSION);
			switch (op) {
				case OPERATION_DATA:{
					String value = UserService.getInstance().readData(header, key);
					if(value!=null) {
						response.status(200);
						return value;
					}else {
						response.status(400);
						return "Error retrieving value; Check Session Token and Key for value";
					}
				}
				default:{
					response.status(400);
					return "Unkown User Operation";
				}
			}
			
		}

		@Override
		public void registerRoutes() {
			get(USER_API,(req,res)->{
				return handleUserGet(req, res);
			});
			/*
			post(Constants.USER_API,(req,res)->{
				return handlePostData(req, res);
			});
			*/
			get(USER_API_OBJECT,(req,res)->{
				return handleSpecifcDataGet(req, res);
			});
			post(USER_API_OBJECT,(req,res)->{
				return handlePostData(req, res);
			});
			
		}
	

	
}
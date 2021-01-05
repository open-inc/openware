package de.openinc.ow.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.DataSubscriber;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

@WebSocket
public class SubscriptionProvider {

	private HashMap<String, List<Session>> sessions;
	private ExecutorService pool;
	private DataSubscriber ds;

	public SubscriptionProvider() {
		this.sessions = new HashMap();
		this.pool = Executors.newFixedThreadPool(4);

		ds = new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getUser() + item.getId())) {
					HashMap<String, List<Session>> tempSession = new HashMap<>();
					tempSession.putAll(sessions);
					pool.execute(new WSDeliverRunnable(tempSession, (OpenWareDataItem) item));
				}
			}
		};
		DataService.addSubscription(ds);

	}

	@OnWebSocketConnect
	public void onConnect(Session user) throws Exception {
		OpenWareInstance.getInstance().logDebug("User connected" + user.getRemoteAddress());
	}

	@OnWebSocketClose
	public void onClose(Session user, int statusCode, String reason) {
		HashMap<String, List<Session>> tempSession = new HashMap<>();
		tempSession.putAll(sessions);
		for (String key : tempSession.keySet()) {
			tempSession.get(key).remove(user);
		}
		sessions = tempSession;
		OpenWareInstance.getInstance().logDebug("User " + user.getRemoteAddress() +
												" disconnected");
		// sessions.get(user).stopThread();

	}

	@OnWebSocketMessage
	public void onMessage(Session user, String message) {

		JSONObject msg = new JSONObject(message);

		OpenWareInstance.getInstance().logDebug("Received message via websocket: " + msg.toString());

		// Messages for subscription need fields action, sensor, user

		if (msg.getString("action").equals("subscribe")) {
			String session = msg.getString("session");
			JSONArray sourceFilter = msg.optJSONArray("sources");
			ArrayList<String> sources = new ArrayList<String>();
			for (int i = 0; i < sourceFilter.length(); i++) {
				sources.add(sourceFilter.optString(i));
			}
			User reqUser = UserService.getInstance().checkAuth(session);
			OpenWareInstance.getInstance().logInfo("New Subscriber:" + reqUser.getName());
			List<OpenWareDataItem> items = DataService
					.getItems(reqUser);
			int count = 0;
			for (OpenWareDataItem item : items) {

				if (sources.contains(item.getUser())) {
					List<Session> cSessions = sessions.getOrDefault(item.getUser() + item.getId(), new ArrayList<>());
					cSessions.add(user);
					sessions.put(item.getUser() + item.getId(), cSessions);
					count++;
				}

			}
			OpenWareInstance.getInstance().logInfo(reqUser.getName() + " subscribed to " +
													count +
													" items");
		}

	}

}

class WSDeliverRunnable implements Runnable {

	private Map<String, List<Session>> sessions;
	private OpenWareDataItem item;

	public WSDeliverRunnable(Map<String, List<Session>> sessions, OpenWareDataItem item) {
		this.sessions = sessions;
		this.item = item;
	}

	@Override
	public void run() {
		for (Session session : sessions.get(item.getUser() + item.getId())) {
			try {
				synchronized (session) {
					if (session.isOpen())
						session.getRemote().sendString(item.toString());
				}
			} catch (IOException e) {
				OpenWareInstance.getInstance().logError(
						"Error while sending via Websocket:\nUser: " + item.getUser() +
														"\nID: " +
														item.getId(),
						e);
			}
		}

	}
}
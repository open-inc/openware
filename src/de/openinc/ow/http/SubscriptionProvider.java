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
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.api.DataSubscriber;
import de.openinc.ow.core.model.data.OpenWareDataItem;
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
			public void receive(OpenWareDataItem item) throws Exception {
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
			OpenWareInstance.getInstance().logInfo("New Subscriber");
			List<OpenWareDataItem> items = DataService
					.getItems(UserService.getInstance().getUser(msg.getString("user")));
			for (OpenWareDataItem item : items) {
				List<Session> cSessions = sessions.getOrDefault(item.getUser() + item.getId(), new ArrayList<>());
				cSessions.add(user);
				sessions.put(item.getUser() + item.getId(), cSessions);
			}
			OpenWareInstance.getInstance().logInfo("Items subscribed: " + items.size());
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
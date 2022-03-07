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
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

@WebSocket
public class SubscriptionProvider {

	private HashMap<String, List<Session>> sessions;
	private ExecutorService pool;
	private DataSubscriber ds;
	private UserService uService;

	public SubscriptionProvider() {
		this.sessions = new HashMap();
		this.pool = Executors.newFixedThreadPool(2);
		this.uService = UserService.getInstance();
		ds = new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getSource() + item.getId())) {
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
	public void onClose(Session wsSession, int statusCode, String reason) {
		HashMap<String, List<Session>> tempSession = new HashMap<>();
		tempSession.putAll(sessions);
		for (String key : tempSession.keySet()) {
			tempSession.get(key).remove(wsSession);
		}
		uService.removeUserSession(wsSession);
		sessions = tempSession;
		OpenWareInstance.getInstance().logDebug("User " + wsSession.getRemoteAddress() +
												" disconnected");
		// sessions.get(user).stopThread();

	}

	@OnWebSocketMessage
	public void onMessage(Session wsSession, String message) {

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

				if (sources.contains(item.getSource())) {
					List<Session> cSessions = sessions.getOrDefault(item.getSource() + item.getId(), new ArrayList<>());
					cSessions.add(wsSession);
					sessions.put(item.getSource() + item.getId(), cSessions);
					uService.addUserSession(reqUser, wsSession);
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
		for (Session session : sessions.get(item.getSource() + item.getId())) {
			try {
				synchronized (session) {
					if (session.isOpen())
						session.getRemote().sendString(item.toString());
				}
			} catch (IOException e) {
				OpenWareInstance.getInstance().logError(
						"Error while sending via Websocket:\nUser: " + item.getSource() +
														"\nID: " +
														item.getId(),
						e);
			}
		}

	}
}
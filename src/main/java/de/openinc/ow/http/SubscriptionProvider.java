package de.openinc.ow.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

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

	private ConcurrentHashMap<String, List<Session>> sessions;
	private ExecutorService pool;
	private DataSubscriber ds;
	private UserService uService;

	public SubscriptionProvider() {
		this.sessions = new ConcurrentHashMap();
		this.pool = OpenWareInstance.getInstance().getCommonExecuteService();
		this.uService = UserService.getInstance();
		ds = new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getSource() + item.getId())) {
					// HashMap<String, List<Session>> tempSession = new HashMap<>();
					// tempSession.putAll(sessions);
					List<Session> relevantSessions = sessions.get(item.getSource() + item.getId());
					if (relevantSessions.size() > 0) {
						// pool.execute(new WSDeliverRunnable(relevantSessions, item));
					}

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
		ConcurrentHashMap<String, List<Session>> tempSession = new ConcurrentHashMap();
		tempSession.putAll(sessions);
		for (String key : tempSession.keySet()) {
			tempSession.get(key).remove(wsSession);
		}
		uService.removeUserSession(wsSession);
		sessions = tempSession;
		OpenWareInstance.getInstance().logDebug("User " + wsSession.getRemoteAddress() + " disconnected");
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
			List<OpenWareDataItem> items = DataService.getItems(reqUser);
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
			OpenWareInstance.getInstance().logInfo(reqUser.getName() + " subscribed to " + count + " items");
		}

	}

}
package de.openinc.ow.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.api.DataSubscriber;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;

public class JavalinWebsocketProvider {

	private ConcurrentHashMap<String, List<WsContext>> sessions;
	private ExecutorService pool;
	private DataSubscriber ds;
	// private UserService uService;

	public JavalinWebsocketProvider() {
		this.sessions = new ConcurrentHashMap<String, List<WsContext>>();
		this.pool = OpenWareInstance.getInstance().getCommonExecuteService();
		// this.uService = UserService.getInstance();
		ds = new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getSource() + item.getId())
						&& sessions.get(item.getSource() + item.getId()).size() > 0) {
					// HashMap<String, List<Session>> tempSession = new HashMap<>();
					// tempSession.putAll(sessions);

					List<WsContext> relevantSessions = new ArrayList();
					relevantSessions.addAll(sessions.get(item.getSource() + item.getId()));
					// new WSDeliver(relevantSessions, item)
					CompletableFuture<List<WsContext>> cf = CompletableFuture.supplyAsync(() -> {
						List<WsContext> errors = new ArrayList<WsContext>();
						for (WsContext session : relevantSessions) {
							try {
								session.send(item.toString());
							} catch (Exception e) {
								OpenWareInstance.getInstance().logError("Error while sending via Websocket:\nUser: "
										+ item.getSource() + "\nID: " + item.getId(), e);
								errors.add(session);
							}
						}
						return errors;
					}, pool);
					cf.whenComplete((res, ex) -> {
						if (ex != null) {
							OpenWareInstance.getInstance().logError(ex);
						}
						if (res.size() > 0) {
							for (WsContext ctx : res) {
								onClose(ctx, 0, "Connection lost");
							}
						}
					});
				}
			}
		};
		DataService.addSubscription(ds);

	}

	public void onConnect(Session user) throws Exception {
		OpenWareInstance.getInstance().logDebug("User connected" + user.getRemoteAddress());
	}

	public void onClose(WsContext wsSession, int statusCode, String reason) {
		ConcurrentHashMap<String, List<WsContext>> tempSession = new ConcurrentHashMap();
		tempSession.putAll(sessions);
		for (String key : tempSession.keySet()) {
			tempSession.get(key).remove(wsSession);
		}
		// uService.removeUserSession(wsSession);
		sessions = tempSession;
		OpenWareInstance.getInstance().logDebug("User " + wsSession.host() + " disconnected");
		// sessions.get(user).stopThread();

	}

	public void onMessage(WsContext wsSession, String message) {

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
					List<WsContext> cSessions = sessions.getOrDefault(item.getSource() + item.getId(),
							new ArrayList<>());
					cSessions.add(wsSession);
					sessions.put(item.getSource() + item.getId(), cSessions);
					// uService.addUserSession(reqUser, wsSession);
					count++;
				}

			}
			OpenWareInstance.getInstance().logInfo(reqUser.getName() + " subscribed to " + count + " items");
		}

	}

	public void registerWSforJavalin(WsConfig ws) {
		ws.onConnect(ctx -> {
			OpenWareInstance.getInstance().logDebug("User connected" + ctx.host());
		});
		ws.onMessage(ctx -> {
			onMessage(ctx, ctx.message());
		});
		ws.onClose(ctx -> {
			this.onClose(ctx, ctx.status(), ctx.reason());
		});
	};

}

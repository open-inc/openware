package de.openinc.ow.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
	// private ExecutorService pool;
	private DataSubscriber ds;
	// private AtomicInteger closedCount;
	// private long lastCloseCountReset;
	// private UserService uService;

	public JavalinWebsocketProvider() {
		this.sessions = new ConcurrentHashMap<String, List<WsContext>>();
		// this.pool = OpenWareInstance.getInstance().getCommonExecuteService();
		// this.uService = UserService.getInstance();
		/*-
		ds = new DataSubscriber() {
		
			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getSource() + item.getId())
						&& sessions.get(item.getSource() + item.getId()).size() > 0) {
					// HashMap<String, List<Session>> tempSession = new HashMap<>();
					// tempSession.putAll(sessions);
		
					// List<WsContext> relevantSessions = new ArrayList();
					// relevantSessions.addAll();
					// new WSDeliver(relevantSessions, item)
					List<WsContext> toUse = sessions.get(item.getSource() + item.getId());
					CompletableFuture<Set<WsContext>> cf = CompletableFuture.supplyAsync(() -> {
						Set<WsContext> errors = new HashSet<WsContext>();
						for (WsContext session : toUse) {
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
								OpenWareInstance.getInstance().logInfo("Calling onClose on unavailable client:"
										+ ctx.getUpgradeReq$javalin().getRemoteSocketAddress());
								onClose(ctx, 0, "Connection lost");
							}
						}
					});
				}
			}
		};
		*/
		ds = new DataSubscriber() {

			@Override
			public void receive(OpenWareDataItem old, OpenWareDataItem item) throws Exception {
				if (sessions.containsKey(item.getSource() + item.getId())
						&& sessions.get(item.getSource() + item.getId()).size() > 0) {
					// HashMap<String, List<Session>> tempSession = new HashMap<>();
					// tempSession.putAll(sessions);

					// List<WsContext> relevantSessions = new ArrayList();
					// relevantSessions.addAll();
					// new WSDeliver(relevantSessions, item)
					List<WsContext> toUse = sessions.get(item.getSource() + item.getId());
					Set<WsContext> errors = new HashSet<WsContext>();
					for (WsContext session : toUse) {
						try {
							session.send(item.toString());
						} catch (Exception e) {
							OpenWareInstance.getInstance().logError("Error while sending via Websocket:\nUser: "
									+ item.getSource() + "\nID: " + item.getId(), e);
							errors.add(session);

						}
					}
					if (errors.size() > 0) {
						for (WsContext ctx : errors) {
							OpenWareInstance.getInstance().logInfo("Calling onClose on unavailable client:"
									+ ctx.getUpgradeReq$javalin().getRemoteSocketAddress());
							System.out.println(ctx.toString());
							onClose(ctx, 0, "Connection lost");
						}
					}

				}
			}
		};
		DataService.addSubscription(ds);

	}

	public void onConnect(Session user) throws Exception {
		OpenWareInstance.getInstance().logDebug("User connected " + user.getRemoteAddress());
		user.setIdleTimeout(Duration.ofMinutes(15));
	}

	public void onClose(WsContext wsSession, int statusCode, String reason) {
//		ConcurrentHashMap<String, List<WsContext>> tempSession = new ConcurrentHashMap();
//		tempSession.putAll(sessions);
		/*-
		int count = closedCount.incrementAndGet();
		long now = System.currentTimeMillis();
		long diff = now - lastCloseCountReset;
		
		if (diff > 1000) {
			System.out.println("WS Close Count: " + count);
			closedCount = new AtomicInteger(0);
			if (count > 100) {
				OpenWareInstance.getInstance()
						.logWarn("To many onClose calls. WS cache seems to be in error state. Resetting cache.");
				resetConnections();
				return;
			}
			lastCloseCountReset = System.currentTimeMillis();
		}
		*/
		for (String key : sessions.keySet()) {
			List cSessions = sessions.get(key);
			synchronized (cSessions) {
				cSessions.remove(wsSession);

			}

		}
		// uService.removeUserSession(wsSession);
		OpenWareInstance.getInstance()
				.logInfo("User " + wsSession.getUpgradeReq$javalin().getRemoteSocketAddress() + " disconnected");

	}

	private synchronized void resetConnections() {
		OpenWareInstance.getInstance().logError("[WEBSOCKET] Resetting connections...");
		HashMap<String, List<WsContext>> holder = new HashMap();
		holder.putAll(sessions);
		sessions.clear();
		HashSet<WsContext> closed = new HashSet<WsContext>();
		for (String key : holder.keySet()) {
			List<WsContext> toClose = holder.getOrDefault(key, new ArrayList());
			OpenWareInstance.getInstance().logError("[WEBSOCKET]" + key + ": " + toClose.size());
			for (WsContext ctx : toClose) {
				if (closed.contains(ctx))
					continue;
				ctx.closeSession(0, "Resetting Websockets");
				closed.add(ctx);
			}

		}

	}

	public void onMessage(WsContext wsSession, String message) {

		JSONObject msg = new JSONObject(message);

		OpenWareInstance.getInstance().logDebug("Received message via websocket: " + msg.toString());

		// Messages for subscription need fields action, sensor, user

		if (msg.getString("action").equals("subscribe")) {
			synchronized (sessions) {
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
								new CopyOnWriteArrayList<>());
						cSessions.add(wsSession);

						sessions.put(item.getSource() + item.getId(), cSessions);
						// uService.addUserSession(reqUser, wsSession);
						count++;
					}

				}
				OpenWareInstance.getInstance().logInfo(reqUser.getName() + " subscribed to " + count + " items");

			}
		}

	}

	public void registerWSforJavalin(WsConfig ws) {
		ws.onConnect(ctx -> {
			OpenWareInstance.getInstance()
					.logDebug("User connected " + ctx.getUpgradeReq$javalin().getRemoteSocketAddress());
		});
		ws.onMessage(ctx -> {
			onMessage(ctx, ctx.message());
		});
		ws.onClose(ctx -> {
			this.onClose(ctx, ctx.status(), ctx.reason());
		});
	};

}

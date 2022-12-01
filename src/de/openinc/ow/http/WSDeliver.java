package de.openinc.ow.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.ow.OpenWareInstance;
import io.javalin.websocket.WsContext;

class WSDeliver implements Callable<List<WsContext>> {

	private List<WsContext> sessions;
	private OpenWareDataItem item;

	public WSDeliver(List<WsContext> sessions, OpenWareDataItem item) {
		this.sessions = sessions;
		this.item = item;
	}

	@Override
	public List<WsContext> call() {
		List<WsContext> errors = new ArrayList<WsContext>();
		for (WsContext session : sessions) {
			try {
				session.send(item.toString());
			} catch (Exception e) {
				OpenWareInstance.getInstance().logError(
						"Error while sending via Websocket:\nUser: " + item.getSource() + "\nID: " + item.getId(), e);
				errors.add(session);
			}
		}
		return errors;
	}
}
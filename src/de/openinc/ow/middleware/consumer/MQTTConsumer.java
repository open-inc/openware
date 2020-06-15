package de.openinc.ow.middleware.consumer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;

/**
 * This class consumes the data from middle-ware (MQTT)
 */
public class MQTTConsumer {

	private String HOST;
	private int PORT;
	private String TOPIC;

	int logQueue = 0;

	private String brokerUrl;
	private String clientId;
	private MqttAsyncClient client;
	private MqttConnectOptions conOpt;
	private MemoryPersistence persistence;

	public MQTTConsumer(String host, int port, String topic) throws IOException, TimeoutException {
		System.out.println("--------------------------------------------------------------------");
		System.out.println("Starting MQTT Consumer");
		this.HOST = host;
		this.PORT = port;
		this.TOPIC = topic;
		persistence = new MemoryPersistence();
		System.out.println("Connecting to Host: " + this.HOST);
		System.out.println("Connecting to Port: " + this.PORT);
		System.out.println("--------------------------------------------------------------------");
		brokerUrl = "tcp://" + this.HOST +
					":" +
					this.PORT;

		try {
			setUpMqtt();
			client.setCallback(new MqttCallback() {
				@Override
				public void connectionLost(Throwable throwable) {
					OpenWareInstance.getInstance().logError("Connection to Websocket lost...");
				}

				@Override
				public void messageArrived(String t, MqttMessage m) throws Exception {

					OpenWareInstance.getInstance().logInfo("New Value: " + new String(m.getPayload()));

					DataService.onNewData(t, new String(m.getPayload()));
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken t) {
					OpenWareInstance.getInstance().logInfo("Complete");
				}
			});
			client.connect(conOpt);
			OpenWareInstance.getInstance().logInfo("Connected");
			Thread.sleep(1000);
			client.subscribe(this.TOPIC, 0);
			OpenWareInstance.getInstance().logInfo("Subscribed");

		} catch (Exception mqe) {
			mqe.printStackTrace();
		}
	}

	public void setUpMqtt() throws MqttException {
		clientId = getClass().getSimpleName() + ((int) (10000 * Math.random()));
		client = new MqttAsyncClient(brokerUrl, clientId, persistence);
		conOpt = new MqttConnectOptions();
		setConOpts(conOpt);
	}

	private void setConOpts(MqttConnectOptions conOpts) {
		conOpts.setCleanSession(false);
		conOpts.setKeepAliveInterval(60);
		conOpts.setAutomaticReconnect(true);
		conOpts.setMaxInflight(65000);
	}

	/*
	 * Alt
	 */
	public void tearDownMqtt() throws MqttException {
		// clean any sticky sessions
		setConOpts(conOpt);
		//client = new MqttClient(brokerUrl, clientId);
		try {
			client.connect(conOpt);
			client.disconnect();
		} catch (Exception e) {
			System.out.println("Client shutdown");
		}
	}

}

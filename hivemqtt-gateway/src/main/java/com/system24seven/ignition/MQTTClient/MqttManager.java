package com.system24seven.ignition.MQTTClient;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE;

public class MqttManager {
    private final Logger logger;
    private Mqtt5AsyncClient client;
    private final ManagedTagProvider tagProvider;

    /**
     * Represents a manager for handling MQTT operations.
     *
     * @param ourProvider - The managed tag provider for the manager
     */
    public MqttManager(ManagedTagProvider ourProvider){
        this.logger = GatewayHook.getLogger(this.getClass());
        this.tagProvider = ourProvider;
    }

    /**
     * This method retrieves an MQTT 5 async client based on the configured settings. If TLS is enabled, it creates the client with SSL configuration, otherwise, it creates the client
     *  without SSL.
     *
     * @return Mqtt5AsyncClient - the MQTT 5 async client instance
     */
    public Mqtt5AsyncClient getMqttClient() {
        try {
          if (false) {
            client = MqttClient.builder()
                    .identifier("ignition" + "-" + UUID.randomUUID())
                    .serverHost("192.168.0.10")
                    .serverPort(8883)
                    .sslWithDefaultConfig()
                    .useMqttVersion5()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
          } else {
            client = MqttClient.builder()
                    .identifier("ignition" + "-" + UUID.randomUUID())
                    .serverHost("192.168.0.10")
                    .serverPort(1883)
                    .useMqttVersion5()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
          }
            return client;
        } catch (Exception e) {
      logger.fatal("Error starting up broker connection.", e);
      return null;
    }
  }

    /**
     * Subscribes to the specified root topic and connects to the provided MQTT client asynchronously.
     *
     * @param client the MQTT 5 async client to subscribe and connect to
     */
  public void subscribeAndConnect(Mqtt5AsyncClient client) {
        try {
            client.subscribeWith()
                    .topicFilter("#")
                    .qos(AT_LEAST_ONCE)
                    .callback(mqtt5Publish -> {
                        try {
                            onMessage(mqtt5Publish);
                        } catch (UnsupportedEncodingException | JSONException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .send()
                    .whenComplete((subAck, throwable) -> logger.trace("Subscribed: " + subAck + ", throwable: " + throwable));

            client.connectWith()
                .noSessionExpiry()
                .simpleAuth()
                .username("")
                .password("".getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .send()
                .whenComplete((mqtt5ConnAck, throwable) -> logger.debug("Connected: " + mqtt5ConnAck + ", throwable: " + throwable));
        } catch (Exception e) {
            logger.fatal("Error starting up broker connection.", e);
        }
  }

    /**
     * Processes the received MQTT message.
     *
     * @param mqtt5Publish the Mqtt5Publish message received
     * @throws UnsupportedEncodingException if character encoding is not supported
     * @throws JSONException if there is an issue with JSON parsing
     */
    private void onMessage(final Mqtt5Publish mqtt5Publish) throws UnsupportedEncodingException, JSONException {
        logger.trace("Received message: " + mqtt5Publish);
        String payload = new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        String baseTopic = removeLastChar(mqtt5Publish.getTopic().toString());

        // Try to parse as JSON and process recursively
        try {
            JSONObject jsonObject = new JSONObject(payload);
            processJsonRecursively(baseTopic, jsonObject);
        } catch (JSONException e) {
            // Not a JSON object, treat as simple value
            tagProvider.updateValue(baseTopic, payload, QualityCode.Good, Date.from(Instant.now()));
        }
    }

    private void processJsonRecursively(String basePath, JSONObject jsonObject) throws JSONException {
        Iterator<String> keys = jsonObject.keys();
        Date timestamp = Date.from(Instant.now());

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.get(key);
            String currentPath = basePath + "/" + key;

            if (value instanceof JSONObject) {
                // Recursively process nested JSON object
                processJsonRecursively(currentPath, (JSONObject) value);
            } else if (value instanceof JSONArray) {
                // Store JSON array as a string
                tagProvider.updateValue(currentPath, value.toString(), QualityCode.Good, timestamp);
            } else {
                // Leaf value - update the tag
                tagProvider.updateValue(currentPath, value.toString(), QualityCode.Good, timestamp);
            }
        }
    }

    private String removeLastChar(String str) {
        if (str != null && !str.isEmpty() && str.charAt(str.length() - 1) == 'x') {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * Publish message to defined topic
     * @param topic String containing topic path
     * @param payload String containing payload value to be published
     */
    public void publishMessage(String topic, String payload) {
        CompletableFuture<Mqtt5PublishResult> result = client.publishWith()
                .topic(topic)
                .qos(AT_LEAST_ONCE)
                .payload(payload.getBytes())
                .send()
                .whenComplete((mqtt5PublishResult, throwable) -> logger.trace("Message Sent: " + mqtt5PublishResult));
    }

    /**
     * Disconnects the MQTT Client
     */
    public void disconnect() {
        this.client.disconnect();
    }

    /**
     * Shuts down the tag provider associated with this MqttManager instance.
     * This method ensures a graceful shutdown of the tag provider by calling its shutdown method with the option to force termination if necessary.
     */
    public void shutdown(){
       tagProvider.shutdown(true);
    }
}

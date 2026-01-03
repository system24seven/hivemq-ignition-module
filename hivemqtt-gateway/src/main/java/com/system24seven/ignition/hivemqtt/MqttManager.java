package com.system24seven.ignition.hivemqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import org.json.JSONException;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE;
import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_MOST_ONCE;

public class MqttManager {
    private final Logger logger;
    private Mqtt5AsyncClient client;
    private final ManagedTagProvider tagProvider;
    private volatile Boolean connected = false;

    /**
     * Represents a manager for handling MQTT operations.
     *
     * @param ourProvider - The managed tag provider for the manager
     */
    public MqttManager(ManagedTagProvider ourProvider){
        this.logger = GatewayHook.getLogger();
        this.tagProvider = ourProvider;
    }

    public Boolean isConnected() {
        return connected;
    }

    /**
     * This method retrieves an MQTT 5 async client based on the configured settings. If TLS is enabled, it creates the client with SSL configuration, otherwise, it creates the client
     *  without SSL.
     *
     * @return Mqtt5AsyncClient - the MQTT 5 async client instance
     */
    public Mqtt5AsyncClient getMqttClient(HiveMqttModuleSettingsResource settings) {
        try {
          if (settings.mqTlsEnable()) {
            client = MqttClient.builder()
                    .identifier("ignition" + "-" + UUID.randomUUID())
                    .serverHost(settings.mqHostname())
                    .serverPort(settings.mqHostPort())
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
                    .serverHost(settings.mqHostname())
                    .serverPort(settings.mqHostPort())
                    .useMqttVersion5()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
          }
            return client;
        } catch (Exception e) {
      logger.error("Error starting up broker connection.", e);
      return null;
    }
  }

    public void subscribeAndConnect(Mqtt5AsyncClient client, HiveMqttModuleSettingsResource settings) {
        connected = false;
        try {
            client
                    .subscribeWith()
                    .topicFilter(settings.mqTopic())
                    .qos(AT_LEAST_ONCE)
                    .callback(this::onMessage)
                    .send()
                    .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .whenComplete(
                            (subAck, throwable) ->{
                                connected = true;
                                logger.trace("Subscribed: " + subAck + ", throwable: " + throwable);
                            });

            client
                    .connectWith()
                    .noSessionExpiry()
                    .simpleAuth()
                    .username(settings.mqUsername())
                    .password(settings.mqPassword().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .whenComplete(
                            (mqtt5ConnAck, throwable) ->
                                    logger.debug("Connected: " + mqtt5ConnAck + ", throwable: " + throwable));
        } catch (Exception e) {
            logger.error("Error starting up broker connection.", e);
        }
        if (client == null) {
            logger.error("MCP-Driver failed to connect to MQTT. Please check your settings.");
        }
    }

    /**
     * Processes the received MQTT message.
     *
     * @param mqtt5Publish the Mqtt5Publish message received
     * @throws UnsupportedEncodingException if character encoding is not supported
     * @throws JSONException if there is an issue with JSON parsing
     */
    private void onMessage(final Mqtt5Publish mqtt5Publish) {
        logger.trace("Received message: " + mqtt5Publish);
        String payload = new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        String baseTopic = removeLastChar(mqtt5Publish.getTopic().toString());

        try {
            tagProvider.updateValue(baseTopic, payload, QualityCode.Good, Date.from(Instant.now()));
        } catch (Exception e) {
            logger.error("Error updating tag value: " + e.getMessage(), e);
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
                .qos(AT_MOST_ONCE)
                .payload(payload.getBytes())
                .send()
                .whenComplete((mqtt5PublishResult, throwable) -> logger.trace("Message Sent: " + mqtt5PublishResult));
    }

    public void publishMessageWithQos(String topic, String payload, MqttQos qualityCode) {
        CompletableFuture<Mqtt5PublishResult> result = client.publishWith()
                .topic(topic)
                .qos(qualityCode)
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

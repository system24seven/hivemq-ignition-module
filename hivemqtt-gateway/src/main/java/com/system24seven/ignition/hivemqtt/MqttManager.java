package com.system24seven.ignition.hivemqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE;

public class MqttManager {
    private final Logger logger;
    private Mqtt5AsyncClient client;
    private final ManagedTagProvider tagProvider;
    private volatile Boolean subscribed = false,connected = false;

    /**
     * Represents a manager for handling MQTT operations.
     *
     * @param tagProvider - The managed tag provider for the manager
     */
    public MqttManager(ManagedTagProvider tagProvider){
        this.logger = GatewayHook.getLogger();
        this.tagProvider = tagProvider;
    }

    public Boolean isConnected() {
        return subscribed && connected;
    }

    public Boolean initMqttClient(HiveMqttModuleSettingsResource settings) {
        buildMqttClient(settings);
        return subscribeAndConnect(settings);
    }
    /**
     * This method retrieves an MQTT 5 async client based on the configured settings. If TLS is enabled, it creates the client with SSL configuration, otherwise, it creates the client
     *  without SSL.
     *
     */
    private void buildMqttClient(HiveMqttModuleSettingsResource settings) {
        try {
          if (settings.mqTlsEnable()) {
            client = Mqtt5Client.builder()
                    .identifier("ignition" + "-" + UUID.randomUUID())
                    .serverHost(settings.mqHostname())
                    .serverPort(settings.mqHostPort())
                    .sslConfig()
                    .trustManagerFactory(InsecureTrustManagerFactory.INSTANCE)
                    .applySslConfig()
                    .simpleAuth()
                        .username(settings.mqUsername())
                        .password(settings.mqPassword().getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
          } else {
            client = Mqtt5Client.builder()
                    .identifier("ignition" + "-" + UUID.randomUUID())
                    .serverHost(settings.mqHostname())
                    .serverPort(settings.mqHostPort())
                    .simpleAuth()
                        .username(settings.mqUsername())
                        .password(settings.mqPassword().getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
          }
        } catch (Exception e) {
      logger.error("Error starting up broker connection.", e);
    }
  }

  private Boolean subscribeAndConnect(HiveMqttModuleSettingsResource settings) {
    subscribed = false;
    connected = false;
    try {
      client
          .connectWith()
          .noSessionExpiry()
          .send()
          .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .whenComplete(
              (mqtt5ConnAck, throwable) -> {
                if (throwable != null) {
                    connected = false;
                    subscribed = false;
                    logger.error("Error connecting to MQTT broker", throwable);
                } else {
                    connected = true;
                    logger.trace("Connected: {}", mqtt5ConnAck);
                }
                  client
                          .subscribeWith()
                          .topicFilter(settings.mqTopic())
                          .qos(AT_LEAST_ONCE)
                          .callback(this::onMessage)
                          .send()
                          .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                          .whenComplete(
                                  (subAck, throwable2) -> {
                                      if (throwable2 != null) {
                                          subscribed = false;
                                          logger.error("Error subscribing to topic: ", throwable2);
                                      } else {
                                          subscribed = true;
                                          logger.trace("Subscribed: {}", subAck);
                                      }
                                  });
              });
    } catch (Exception e) {
      logger.error("Error starting up broker connection.", e);
    }

    return connected && subscribed;
  }
    /**
     * Called on new published message on subscribed topic
     *
     * @param mqtt5Publish the Mqtt5Publish message received
     */
    private void onMessage(final Mqtt5Publish mqtt5Publish) {
        logger.trace("Received message: {}", mqtt5Publish);
        String payload = new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        String baseTopic = removeLastChar(mqtt5Publish.getTopic().toString());

        try {
            tagProvider.updateValue(baseTopic, payload, QualityCode.Good, Date.from(Instant.now()));
        } catch (Exception e) {
            try {
                tagProvider.updateValue(baseTopic + "/@Self", payload, QualityCode.Bad, Date.from(Instant.now()));
            } catch (Exception e1) {
                logger.error("Error updating tag value: ", e);
            }
        }
    }


    private String removeLastChar(String str) {
        if (str != null && !str.isEmpty() && str.charAt(str.length() - 1) == 'x') {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public void publishMessageWithQos(String topic, String payload, MqttQos qualityCode) {
        CompletableFuture<Mqtt5PublishResult> result = client.publishWith()
                .topic(stripLeadingBrackets(topic))
                .qos(qualityCode)
                .payload(payload.getBytes())
                .send()
                .whenComplete((mqtt5PublishResult, throwable) -> logger.trace("Message Sent: " + mqtt5PublishResult));
    }

    /**
     * Strips the leading tagprovider and brackets from the topic.
     * Ignition injects them automatically.
     * @param str
     * @return
     */
    public String stripLeadingBrackets(String str) {
        if (str == null) return str;
        return str.replaceFirst("^\\[.*?\\]", "");
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
        disconnect();
        tagProvider.shutdown(true);
    }
}

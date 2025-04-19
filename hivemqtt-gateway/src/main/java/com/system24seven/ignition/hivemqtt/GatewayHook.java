package com.system24seven.ignition.hivemqtt;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ProviderConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONException;

import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE;

public class GatewayHook extends AbstractGatewayModuleHook {
    private Logger logger;
    private GatewayContext context;
    private ManagedTagProvider ourProvider;
    private Mqtt5AsyncClient client;

    public GatewayHook() {
        logger = LogManager.getLogger(this.getClass());
    }

    @Override
    public void setup(GatewayContext context) {
        try {
            this.context = context;
            ProviderConfiguration configuration = new ProviderConfiguration("HiveMQTTClient");

            // Needed to allow tag configuration to be editable. Comment this out to disable tag configuration editing.
            configuration.setAllowTagCustomization(true);
            configuration.setPersistTags(true);
            configuration.setPersistValues(true);
            configuration.setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false);

            ourProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
        } catch (Exception e) {
            logger.fatal("Error setting up HiveMQTT Tag Provider.", e);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        try {
            //Register a task with the execution system to update values every second.
            //context.getExecutionManager().register(getClass().getName(), TASK_NAME, this::updateValues, 1000);

            logger.info("HiveMQTT Tag Provider module started.");
        } catch (Exception e) {
            logger.fatal("Error starting up HiveMQTT Tag Provider module.", e);
        }
        try {
            client = MqttClient.builder()
                    .identifier("ignition"+"-"+ UUID.randomUUID())
                    .serverHost("mqtt.shadowcontrols.com")
                    .serverPort(8883)
                    .sslWithDefaultConfig()
                    .useMqttVersion5()
                    .executorConfig()
                    .nettyThreads(1)
                    .applyExecutorConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
            subscribeAndConnect(client);

            logger.info("Connected to MQTT broker");


        } catch (Exception e) {
            logger.fatal("Error starting up broker connection.", e);
        }

    }

    @Override
    public void shutdown() {
        //Clean up the things we've registered with the platform, namely, our provider type.
        try {
            if (context != null) {
                //shutdown the mqtt connection
                client.disconnect();
                //Shutdown our provider (and delete all data)
                ourProvider.shutdown(true);
            }
        } catch (Exception e) {
            logger.error("Error stopping HiveMQTT Tag Provider module.", e);
        }
        logger.info("HiveMQTT Tag Provider module stopped.");
    }

    private void onMessage(final Mqtt5Publish mqtt5Publish, final Mqtt5AsyncClient client) throws UnsupportedEncodingException, JSONException {
        Logger logger2 = LogManager.getLogger("ManagedProviderGatewayHook");
        logger2.trace("Received message: " + mqtt5Publish);
        String payload = new String(mqtt5Publish.getPayloadAsBytes(), "UTF-8"); //Grab message payload as string
        //JSONObject jsonPayload = new JSONObject(payload);
        //ourProvider.configureTag(removeLastChar(mqtt5Publish.getTopic().toString()),
               // DataType.Document);
        ourProvider.updateValue(removeLastChar(mqtt5Publish.getTopic().toString()) ,
                payload,
                QualityCode.Good,
                Date.from(Instant.now()));
        //logger2.info("Content: " + new String(mqtt5Publish.getPayloadAsBytes(), "UTF-8"));

    }

    private String removeLastChar(String str) {
        if (str != null && !str.isEmpty() && str.charAt(str.length() - 1) == 'x') {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    private void subscribeAndConnect(final Mqtt5AsyncClient client) {
        Logger logger3 = LogManager.getLogger("GatewayHook");
        client.subscribeWith()
                .topicFilter("#")
                .qos(AT_LEAST_ONCE)
                .callback(mqtt5Publish -> {
                    try {
                        onMessage(mqtt5Publish, client);
                    } catch (UnsupportedEncodingException | JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    System.out.println("Subscribed: " + subAck + ", throwable: " + throwable);
                });

        client.connectWith()
                .noSessionExpiry()
                .simpleAuth()
                .username("")
                .password("".getBytes())
                .applySimpleAuth()
                .send()
                .whenComplete((mqtt5ConnAck, throwable) -> {
                    logger3.info("Connected: " + mqtt5ConnAck + ", throwable: " + throwable);
                });
    }

}

package com.system24seven.ignition.MQTTClient;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ProviderConfiguration;
import com.inductiveautomation.ignition.gateway.web.models.ConfigCategory;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import com.system24seven.ignition.MQTTClient.records.MQSettingsRecord;
import com.system24seven.ignition.MQTTClient.settings.MQSettingsPage;
import com.system24seven.ignition.MQTTClient.settings.SettingsManager;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONException;

import static com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE;

public class GatewayHook extends AbstractGatewayModuleHook {
    private Logger logger;
    private GatewayContext context;
    private ManagedTagProvider ourProvider;
    private Mqtt5AsyncClient client;
    public static final String BUNDLE_PREFIX = "MQTTClient";
    public static final String BUNDLE_FILE_NAME = "MQTTClient";
    private SettingsManager _settingsManager;
    private MQSettingsRecord settingsRecord;

    public static Logger getLogger(Class c) {
        String root = "HiveMQ-Client";
        if (c == null) {
            return LogManager.getLogger(root);
        }
        var names = c.getName().split("\\.");
        var name = names[names.length - 1];
        var logger = LogManager.getLogger(root + "_" + name);
        logger.setLevel(Level.TRACE);
        return logger;
    }
    /**
   ` * Set up the config menu category for the page
     */
    public static final ConfigCategory CONFIG_CATEGORY =
            new ConfigCategory("MQTTClient",
                    BUNDLE_PREFIX + ".nav.header", 700);


    public GatewayHook() {
        logger = LogManager.getLogger(this.getClass());
    }

    @Override
    public void setup(GatewayContext context) {
        // Register MQSettingsPage.properties by registering the GatewayHook.class with BundleUtils
        BundleUtil.get().addBundle(BUNDLE_PREFIX, getClass(), BUNDLE_FILE_NAME);
        this.context = context;
        verifySchema();
        MQSettingsPage.setup(this);

        try {
            _settingsManager = new SettingsManager(context, this);
        } catch (Exception e) {
            logger.fatal("Error loading configuration: " + e.getMessage(), e);
            return;
        }

        settingsRecord = context.getLocalPersistenceInterface().find(MQSettingsRecord.META, 0L);



        try {
            ProviderConfiguration configuration = new ProviderConfiguration("MQTTClient");

            // Needed to allow tag configuration to be editable. Comment this out to disable tag configuration editing.
            configuration.setAllowTagCustomization(true);
            configuration.setPersistTags(true);
            configuration.setPersistValues(true);
            configuration.setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false);

            ourProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
        } catch (Exception e) {
            logger.fatal("Error setting up MQTT Tag Provider.", e);
        }
    }

    private void verifySchema() {
        try {
            context.getSchemaUpdater().updatePersistentRecords(MQSettingsRecord.META);
        } catch (SQLException e) {
            logger.error("Unable to create required internal DB table", e);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        try {
            //Register a task with the execution system to update values every second.
            //context.getExecutionManager().register(getClass().getName(), TASK_NAME, this::updateValues, 1000);

            logger.info("MQTT Tag Provider module started.");
        } catch (Exception e) {
            logger.fatal("Error starting up MQTT Tag Provider module.", e);
        }

        try {
            _settingsManager.startup();
        } catch (Exception e) {
            logger.fatal("Error starting settings manager: " + e.getMessage(), e);
            try {
                _settingsManager.shutdown();
            } catch (Exception e2) {
                logger.error("Error stopping settings manager after failed start: " + e2.getMessage(), e);
            }
            return;
        }
        try {
            if(settingsRecord.getMQTlsEnable()){
                client =
                        MqttClient.builder()
                                .identifier("ignition" + "-" + UUID.randomUUID())
                                .serverHost(settingsRecord.getMQHostname())
                                .serverPort(settingsRecord.getMQHostPort())
                                .sslWithDefaultConfig()
                                .useMqttVersion5()
                                .executorConfig()
                                .nettyThreads(1)
                                .applyExecutorConfig()
                                .automaticReconnectWithDefaultConfig()
                                .buildAsync();
            } else {
                client =
                        MqttClient.builder()
                                .identifier("ignition" + "-" + UUID.randomUUID())
                                .serverHost(settingsRecord.getMQHostname())
                                .serverPort(settingsRecord.getMQHostPort())
                                .useMqttVersion5()
                                .executorConfig()
                                .nettyThreads(1)
                                .applyExecutorConfig()
                                .automaticReconnectWithDefaultConfig()
                                .buildAsync();
            }

            subscribeAndConnect(client);

            logger.info("Connected to MQTT broker");


        } catch (Exception e) {
            logger.fatal("Error starting up broker connection.", e);
        }

    }

    @Override
    public void shutdown() {
        /* remove our bundle */
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);
        MQSettingsPage.shutdown();
        //Clean up the things we've registered with the platform, namely, our provider type.
        try {
            if (context != null) {
                //shutdown the mqtt connection
                client.disconnect();
                //Shutdown our provider (and delete all data)
                ourProvider.shutdown(true);
            }
        } catch (Exception e) {
            logger.error("Error stopping MQTT Tag Provider module.", e);
        }
        logger.info("MQTT Tag Provider module stopped.");
    }

    @Override
    public List<ConfigCategory> getConfigCategories() {
        return Collections.singletonList(CONFIG_CATEGORY);
    }

    @Override
    public List<? extends IConfigTab> getConfigPanels() {
        return Collections.singletonList(MQSettingsPage.CONFIG_ENTRY);
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
        Logger logger3 = LogManager.getLogger("MQTTClient");
        client.subscribeWith()
                .topicFilter(settingsRecord.getTopicName())
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
                .username(settingsRecord.getMQUsername())
                .password(settingsRecord.getMQPassword().getBytes())
                .applySimpleAuth()
                .send()
                .whenComplete((mqtt5ConnAck, throwable) -> {
                    logger3.info("Connected: " + mqtt5ConnAck + ", throwable: " + throwable);
                });
    }

}

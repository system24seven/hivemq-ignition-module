package com.system24seven.ignition.MQTTClient;

import java.util.List;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.tags.config.TagProviderValuePersistence;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceExtensionPoint;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProviderConfiguration;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class GatewayHook extends AbstractGatewayModuleHook {
    private final Logger logger;
    private GatewayContext context;
    private static MqttManager mqttManager;
    public static final String BUNDLE_PREFIX = "MQTTClient";
    public static final String BUNDLE_FILE_NAME = "MQTTClient";
    private ManagedTagProvider ourProvider;
    private static final String TASK_NAME = "UpdateSampleValues";

    /**
     * Returns a Logger instance for the specified class.
     *
     * @param c The class for which to retrieve the Logger instance.
     * @return Logger instance for the specified class.
     */
    public static Logger getLogger(Class<?> c) {
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

    public GatewayHook() {
        logger = LogManager.getLogger(this.getClass());
    }

    public static MqttManager getMqttManager() {
        return mqttManager;
    }

    @Override
    public void setup(GatewayContext context) {
        // Register MQSettingsPage.properties by registering the GatewayHook.class with BundleUtils
        BundleUtil.get().addBundle(BUNDLE_PREFIX, getClass(), BUNDLE_FILE_NAME);
        this.context = context;

        try {
            ManagedTagProviderConfiguration configuration = ManagedTagProviderConfiguration.builder("MQTTClient")
                    .persistTags(false)
                    // Needed to allow tag configuration to be editable. Comment this out to disable tag configuration
                    // editing.
                    .allowTagCustomization(true)
                    // The valuePersistence setting controls where Managed Tag value are stored. Given Managed Tags are
                    // procedurally generated, TagProviderValuePersistence.None makes the most sense as this will keep
                    // tag values in memory only, and not persist them to the database or configuration files.
                    .valuePersistence(TagProviderValuePersistence.None)
                    .setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false)
                    .build();

            ourProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
            ourProvider.registerWriteHandler("/*", (TagPath target, Object value) -> {
                Integer intVal = TypeUtilities.toInteger(value);
                //The adjustTags function will add/remove tags, AND update the current value of the control tag.
                handleWrite(target, value);
                return QualityCode.Good;
            });
        } catch (Exception e) {
            logger.fatal("Error setting up MQTT Tag Provider.", e);
        }

        try {
            mqttManager = new MqttManager(ourProvider);
        } catch (Exception e){
            logger.fatal("Error loading MQTT manager: " + e.getMessage(), e);
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

        Mqtt5AsyncClient client = mqttManager.getMqttClient();
        mqttManager.subscribeAndConnect(client);
        logger.debug("Connected to MQTT broker");
    }

    @Override
    public void shutdown() {
        /* remove our bundle */
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);

        //Clean up the things we've registered with the platform, namely, our provider type.
        try {
            if (context != null) {
                //shutdown the mqtt connection
                mqttManager.disconnect();
                //Shutdown our provider (and delete all data)
                mqttManager.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error stopping MQTT Tag Provider module.", e);
        }
        logger.info("MQTT Tag Provider module stopped.");
    }

    private void handleWrite(TagPath target, Object value) {
        logger.debug("Writing value: " + value + " to tag: " + target);
        mqttManager.publishMessage(target.toString(), value.toString());
    }
}

package com.system24seven.ignition.hivemqtt;

import java.util.Optional;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.tags.config.TagProviderValuePersistence;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.config.SingletonResourceHandler;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProviderConfiguration;
import com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule;
import com.system24seven.ignition.hivemqtt.routes.apiEndpoints;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayHook extends AbstractGatewayModuleHook {
    private static final Logger logger = LoggerFactory.getLogger("hivemqtt");
    private GatewayContext context;
    private static MqttManager mqttManager;
    private ManagedTagProvider tagProvider;
    private HiveMqttModuleSettingsResource settingsResource;
    private static GatewayHook instance;

    public static Logger getLogger() {
        return logger;
    }

    @Override
    public void setup(GatewayContext context) {
        this.context = context;
        instance = this;

        try {
            ManagedTagProviderConfiguration configuration = ManagedTagProviderConfiguration.builder("MQTTClient")
                    .persistTags(false)
                    .allowTagCustomization(true)
                    .valuePersistence(TagProviderValuePersistence.None)
                    .setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false)
                    .build();

            tagProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
            tagProvider.registerWriteHandler("/*", (TagPath target, Object value) -> {
                handleWrite(target, value);
                return QualityCode.Good;
            });
        } catch (Exception e) {
            logger.error("Error setting up MQTT Tag Provider.", e);
        }

        try {
            mqttManager = new MqttManager(tagProvider);
        } catch (Exception e){
            logger.error("Error loading MQTT manager: " + e.getMessage(), e);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        SystemJsModule jsModule =
                new SystemJsModule(
                        "com.system24seven.ignition.mcpplay.webui", "/res/hivemqtt/MqttSettingsPage.js");
        context
                .getWebResourceManager()
                .getNavigationModel()
                .getConnections()
                .addCategory(
                        "webuiwebpage",
                        cat ->
                                cat.label("MQTT Client")
                                        .addPage(
                                                "MQTT Tag Driver",
                                                page ->
                                                        page.position(10)
                                                                // Note the second parameter is the name of the JS component that
                                                                // was exported
                                                                .mount("/hivemqtt", "MqttSettingsPage", jsModule)));

        context
                .getConfigurationManager()
                .getResourceTypeMetaRegistry()
                .register(HiveMqttModuleSettingsResource.META);

        // register changes in the settings and handle
        SingletonResourceHandler<HiveMqttModuleSettingsResource> singletonResourceHandler = SingletonResourceHandler.newBuilder(HiveMqttModuleSettingsResource.META)
                .context(context)
                .onChange(this::updateSettings)
                .build();

        settingsResource = singletonResourceHandler.getResource();

        Mqtt5AsyncClient client = mqttManager.getMqttClient(settingsResource);
        mqttManager.subscribeAndConnect(client, settingsResource);
        logger.debug("Connected to MQTT broker");
    }

    @Override
    public void shutdown() {
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

    // Settings update handler
    public void updateSettings(HiveMqttModuleSettingsResource settingsRecord) {
        mqttManager.disconnect();
        settingsResource = settingsRecord;
        Mqtt5AsyncClient client = mqttManager.getMqttClient(settingsResource);
        mqttManager.subscribeAndConnect(client, settingsResource);
        logger.debug("Updated MQTT broker settings");
    }

    public HiveMqttModuleSettingsResource getSettings() {
        return settingsResource;
    }

    /**
     * @return the path to a folder in one of the module's gateway jar files that should be mounted at
     *     /res/module-id/foldername
     */
    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.of("mounted");
    }

    /**
     * Provides a chance for the module to mount any route handlers it wants. These will be active at
     * <tt>/data/module-id/*</tt> See {@link RouteGroup} for details. Will be called after startup().
     */
    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        apiEndpoints.mountRoutes(routes, this);
    }

    /**
     * Used by the mounting underneath /res/module-id/* and /data/module-id/* as an alternate mounting
     * path instead of your module id, if present.
     */
    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of("hivemqtt");
    }

    /**
     * Called prior to a 'mounted resource request' being fulfilled by requests to the mounted
     * resource servlet serving resources from /res/module-id/ (or /res/alias/ if {@link
     * GatewayHook#getMountPathAlias} is implemented). It is called after the target resource
     * has been successfully located.
     *
     * <p>Primarily intended as an opportunity to amend/alter the response's headers for purposes such
     * as establishing Cache-Control. By default, Ignition sets no additional headers on a resource
     * request.
     *
     * @param resourcePath path to the resource being returned by the mounted resource request
     * @param response the response to read/amend.
     */
    @Override
    public void onMountedResourceRequest(String resourcePath, HttpServletResponse response) {}

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        manager.addScriptModule(
                "system.mqtt",
                new GatewayScriptModule(),
                new PropertiesFileDocProvider());
    }

    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(GatewayRpcImplementation.of(
                RpcFunctions.SERIALIZER,
                new RpcFunctionsImpl()
        ));
    }

    public void publishMessageWithQos(String topic, String payload, int qos) {
        logger.debug("Writing value: " + payload + " to tag: " + topic);
        mqttManager.publishMessageWithQos(
            topic, payload, MqttQos.fromCode(qos));
        }

    public static GatewayHook getInstance() {
        return instance;
    }
}

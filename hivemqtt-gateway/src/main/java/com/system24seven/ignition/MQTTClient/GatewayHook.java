package com.system24seven.ignition.MQTTClient;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.tags.config.TagProviderValuePersistence;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.config.SingletonResourceHandler;
import com.inductiveautomation.ignition.gateway.dataroutes.AccessControlStrategy;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProviderConfiguration;
import com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private SingletonResourceHandler singletonResourceHandler;
    private HiveMqttModuleSettingsResource settingsResource;

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

            logger.info("MQTT Tag Provider module started.");
        } catch (Exception e) {
            logger.fatal("Error starting up MQTT Tag Provider module.", e);
        }

        SystemJsModule jsModule =
                new SystemJsModule(
                        "com.system24seven.ignition.mcpplay.webui", "/res/hivemqtt/MqttSettingsPage.js");

        // note that we are adding the nav to the "home" section of the nav by using getHome(), but this
        // can be added
        // to different sections by using the appropriate method (IE: getPlatform(), getConnections(),
        // getNetwork(), ect)
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
        singletonResourceHandler =
                SingletonResourceHandler.newBuilder(HiveMqttModuleSettingsResource.META)
                        .context(context)
                        .onChange(this::updateSettings)
                        .build();

        settingsResource = (HiveMqttModuleSettingsResource) singletonResourceHandler.getResource();

        this.context = context;

        Mqtt5AsyncClient client = mqttManager.getMqttClient(settingsResource);
        mqttManager.subscribeAndConnect(client, settingsResource);
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

    // Settings update handler
    private void updateSettings(HiveMqttModuleSettingsResource settingsRecord) {
        mqttManager.disconnect();
        Mqtt5AsyncClient client = mqttManager.getMqttClient(settingsResource);
        mqttManager.subscribeAndConnect(client, settingsResource);
        logger.debug("Updated MQTT broker settings");
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
        logger.debug("=== MOUNTING ROUTE HANDLERS ===");

        routes
                .newRoute("/api/settings")
                .handler(
                        (ctx, response) -> {
                            HttpServletRequest request = ctx.getRequest();
                            logger.debug(
                                    "API handler invoked: " + request.getMethod() + " " + request.getRequestURI());

                            try {
                                response.setContentType("application/json");
                                // logger.info("Request: " + request.getMethod());

                                if ("GET".equals(request.getMethod())) {
                                    JsonObject json = new JsonObject();
                                    json.addProperty("mqHostname", settingsResource.mqHostname());
                                    json.addProperty("mqHostPort", settingsResource.mqHostPort());
                                    json.addProperty("mqUsername", settingsResource.mqUsername());
                                    json.addProperty("mqPassword", settingsResource.mqPassword());
                                    json.addProperty("mqTlsEnable", settingsResource.mqTlsEnable());

                                    response.getWriter().write(json.toString());
                                    logger.info("Settings retrieved successfully");

                                } else {
                                    response.setStatus(405);
                                    response.getWriter().write("{\"error\":\"Method not allowed\"}");
                                }

                            } catch (Exception e) {
                                logger.error("Error in API handler", e);
                                response.setStatus(500);
                                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                            }

                            return null; // or return response if needed
                        })
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes
                .newRoute("/api/settings")
                .handler(
                        (ctx, response) -> {
                            HttpServletRequest request = ctx.getRequest();
                            logger.debug(
                                    "API handler invoked: " + request.getMethod() + " " + request.getRequestURI());

                            try {
                                response.setContentType("application/json");
                                // logger.info("Request: " + request.getMethod());

                                if ("POST".equals(request.getMethod())) {
                                    String body =
                                            new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                                    // logger.info("POST body: " + body);

                                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                                    HiveMqttModuleSettingsResource newSettings =
                                            new HiveMqttModuleSettingsResource(
                                                    json.has("mqHostname") ? json.get("mqHostname").getAsString() : "",
                                                    json.has("mqHostPort") ? json.get("mqHostPort").getAsInt() : 1883,
                                                    json.has("mqUsername") ? json.get("mqUsername").getAsString() : "",
                                                    json.has("mqPassword") ? json.get("mqPassword").getAsString() : "",
                                                    json.has("mqTopic") ? json.get("mqTopic").getAsString() : "",
                                                    json.has("mqTlsEnable") && json.get("mqTlsEnable").getAsBoolean());

                                    // Save using the singleton handler
                                    settingsResource = newSettings;
                                    updateSettings(settingsResource);

                                    response.getWriter().write("{\"success\":true}");
                                    logger.info("Settings saved successfully");
                                }
                            } catch (Exception e) {
                                logger.error("Error in API handler", e);
                                response.setStatus(500);
                                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                            }

                            return null; // or return response if needed
                        })
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        logger.info("=== ROUTE HANDLERS MOUNTED ===");
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
}

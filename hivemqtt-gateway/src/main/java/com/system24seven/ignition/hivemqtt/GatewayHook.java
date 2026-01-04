package com.system24seven.ignition.hivemqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.resourcecollection.PushException;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayHook extends AbstractGatewayModuleHook {
  private static final Logger logger = LoggerFactory.getLogger("hivemqtt");
  private GatewayContext context;
  private static MqttManager mqttManager;
  private ManagedTagProvider tagProvider;
  private HiveMqttModuleSettingsResource settingsResource;
  private static GatewayHook instance;
  private SingletonResourceHandler<HiveMqttModuleSettingsResource> settingsHandler;

  public static Logger getLogger() {
    return logger;
  }

  @Override
  public void setup(GatewayContext context) {
    this.context = context;
    instance = this;

    initialiseTagProvider(context);

    try {
      mqttManager = new MqttManager(tagProvider);
    } catch (Exception e) {
      logger.error("Error loading MQTT manager: {}", e.getMessage(), e);
    }
  }

  /**
   * Initializes a managed tag provider for the system, allowing for dynamic tag management and
   * customization. Configures the tag provider with specific settings such as disabling tag
   * persistence and enabling tag customization. Additionally, registers a write handler for tag
   * updates, ensuring that changes to tag values are processed appropriately.
   *
   * @param context the gateway context used to create and manage the tag provider
   */
  private void initialiseTagProvider(GatewayContext context) {
    try {
      ManagedTagProviderConfiguration configuration =
          ManagedTagProviderConfiguration.builder("MQTTClient")
              .persistTags(false)
              .allowTagCustomization(true)
              .valuePersistence(TagProviderValuePersistence.None)
              .setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false)
              .build();

      tagProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
      tagProvider.registerWriteHandler(
          "/*",
          (TagPath target, Object value) -> {
            mqttManager.publishMessageWithQos(
                target.toString(), value.toString(), MqttQos.AT_LEAST_ONCE);
            return QualityCode.Good;
          });
    } catch (Exception e) {
      logger.error("Error setting up MQTT Tag Provider.", e);
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
    settingsHandler =
        SingletonResourceHandler.newBuilder(HiveMqttModuleSettingsResource.META)
            .context(context)
            .onChange(this::updateSettings)
            .build();
    settingsHandler.startup();

    settingsResource = settingsHandler.getResource();

    if (mqttManager.initMqttClient(settingsResource)) {
      logger.info("MQTT broker connection established.");
    }
  }

  @Override
  public void shutdown() {
    // Clean up the things we've registered with the platform, namely, our provider type.
    try {
      if (context != null) {
        // shutdown the mqtt connection
        mqttManager.disconnect();
        // Shutdown our provider (and delete all data)
        mqttManager.shutdown();
      }
    } catch (Exception e) {
      logger.error("Error stopping MQTT Tag Provider module.", e);
    }
    logger.info("MQTT Tag Provider module stopped.");
  }

  /**
   * Updates the MQTT settings and reinitializes the MQTT client with the new settings. This method
   * disconnects the existing client, updates the settings resource, establishes a new MQTT client,
   * and subscribes to the specified topic.
   *
   * @param settingsRecord the new settings object containing configuration values for the MQTT
   *     broker, authentication, topic, TLS, etc.
   */
  public void updateSettings(HiveMqttModuleSettingsResource settingsRecord) {
    if(getMqttStatus()){
      mqttManager.disconnect();
    }
    try {
      if (!settingsRecord.equals(settingsResource)) {
        settingsHandler.updateResource(settingsRecord);
        settingsResource = settingsHandler.getResource();
      }
    } catch (PushException e) {
      logger.error("Error updating MQTT broker settings: {}{}", e, e.getMessage());
    }
    if (mqttManager.initMqttClient(settingsResource)) {
      logger.debug("MQTT broker connection reestablished.");
    }
  }

  /** Pulls the current settings reference. */
  public HiveMqttModuleSettingsResource getSettings() {
    return settingsResource;
  }

  public boolean getMqttStatus() {
    return mqttManager.isConnected();
  }

  /**
   * Retrieves the folder name for mounted resources.
   *
   * @return an {@link Optional} containing the folder name string "mounted" if it exists.
   */
  @Override
  public Optional<String> getMountedResourceFolder() {
    return Optional.of("mounted");
  }

  /** Called to mount the api routes for the settings page */
  @Override
  public void mountRouteHandlers(RouteGroup routes) {
    apiEndpoints.mountRoutes(routes, this);
  }

  @Override
  public Optional<String> getMountPathAlias() {
    return Optional.of("hivemqtt");
  }

  @Override
  public void onMountedResourceRequest(String resourcePath, HttpServletResponse response) {}

  @Override
  public void initializeScriptManager(ScriptManager manager) {
    super.initializeScriptManager(manager);

    manager.addScriptModule(
        "system.mqtt", new GatewayScriptModule(), new PropertiesFileDocProvider());
  }

  /**
   * Returns an {@link Optional} containing the {@link GatewayRpcImplementation} if the
   * implementation is available. This provides the RPC functionality required to handle remote
   * procedure calls for the gateway module, using the specified {@link RpcFunctions} serializer and
   * {@link RpcFunctionsImpl} implementation.
   *
   * @return an {@code Optional} containing the {@code GatewayRpcImplementation} if successfully
   *     initialized, otherwise an empty {@code Optional}.
   */
  @Override
  public Optional<GatewayRpcImplementation> getRpcImplementation() {
    return Optional.of(
        GatewayRpcImplementation.of(RpcFunctions.SERIALIZER, new RpcFunctionsImpl()));
  }

  public static GatewayHook getInstance() {
    return instance;
  }

  public MqttManager getMqttManager() {
    return mqttManager;
  }
}

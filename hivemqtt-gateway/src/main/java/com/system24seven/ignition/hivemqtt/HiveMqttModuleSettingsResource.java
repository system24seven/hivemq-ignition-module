package com.system24seven.ignition.hivemqtt;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.*;
import com.inductiveautomation.ignition.gateway.web.nav.FormFieldType;

public record HiveMqttModuleSettingsResource(
    @FormCategory("MQTT Broker")
        @Required
        @Label("Broker IP")
        @Description("MQTT Broker IP or Hostname")
        @FormField(FormFieldType.TEXT)
        String mqHostname,
    @FormCategory("MQTT Broker")
        @Label("Broker Port")
        @Required
        @DefaultValue("1883")
        @Maximum("65535")
        @Minimum("0")
        @Description("MQTT Broker TCP Port")
        @FormField(FormFieldType.NUMBER)
        Integer mqHostPort,
    @FormCategory("MQTT Broker") @Label("Username") @FormField(FormFieldType.TEXT)
        String mqUsername,
    @FormCategory("MQTT Broker") @Label("Password") @FormField(FormFieldType.TEXT)
        String mqPassword,
    @FormCategory("MQTT Topic") @Label("Topic")
    @FormField(FormFieldType.TEXT)
    @Required
    @DefaultValue("#")
        String mqTopic,
    @FormCategory("MQTT Broker") @Label("Enable TLS") @FormField(FormFieldType.CHECKBOX)
        Boolean mqTlsEnable) {
  public static final ResourceType TYPE = new ResourceType("com.system24seven.ignition.hivemqtt", "settings");

  public static final HiveMqttModuleSettingsResource DEFAULT =
      new HiveMqttModuleSettingsResource("192.168.0.10", 1883, "", "", "#", false);

  public static final ResourceTypeMeta<HiveMqttModuleSettingsResource> META =
      ResourceTypeMeta.newBuilder(HiveMqttModuleSettingsResource.class)
          .resourceType(TYPE)
          .singleton()
          .defaultConfig(DEFAULT)
          .categoryName("HiveMqttSettingsResource")
          .build();
}

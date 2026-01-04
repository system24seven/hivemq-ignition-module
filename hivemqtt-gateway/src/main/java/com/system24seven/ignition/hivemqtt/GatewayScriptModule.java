package com.system24seven.ignition.hivemqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;

public class GatewayScriptModule extends AbstractScriptModule {
    public GatewayScriptModule() {
    }

    @Override
    protected void publishMessage(String topic, String payload, int qos) {
        GatewayHook.getInstance().getMqttManager().publishMessageWithQos(topic, payload, MqttQos.fromCode(qos));
    }
}

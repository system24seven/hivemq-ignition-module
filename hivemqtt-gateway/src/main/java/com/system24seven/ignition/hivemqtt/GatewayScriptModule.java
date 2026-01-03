package com.system24seven.ignition.hivemqtt;

public class GatewayScriptModule extends AbstractScriptModule {
    public GatewayScriptModule() {
    }

    @Override
    protected void publishMessage(String topic, String payload, int qos) {
        GatewayHook instance = GatewayHook.getInstance();
        instance.publishMessageWithQos(topic, payload, qos);
    }
}

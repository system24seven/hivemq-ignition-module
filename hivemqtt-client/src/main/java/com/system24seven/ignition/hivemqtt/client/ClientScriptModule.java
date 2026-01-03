package com.system24seven.ignition.hivemqtt.client;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import com.system24seven.ignition.hivemqtt.AbstractScriptModule;
import com.system24seven.ignition.hivemqtt.Constants;
import com.system24seven.ignition.hivemqtt.RpcFunctions;

public class ClientScriptModule extends AbstractScriptModule {

    private static final RpcFunctions RPC = GatewayConnection.getRpcInterface(
            RpcFunctions.SERIALIZER,
            Constants.MODULE_ID,
            RpcFunctions.class
    );

    public ClientScriptModule() {}

    @Override
    protected void publishMessage(String topic, String payload, int qos) {
        RPC.publishMessage(topic, payload, qos);
    }
}

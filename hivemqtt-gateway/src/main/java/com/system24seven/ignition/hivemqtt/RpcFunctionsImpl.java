package com.system24seven.ignition.hivemqtt;

import com.inductiveautomation.ignition.common.project.ClientPermissionsConstants;
import com.inductiveautomation.ignition.gateway.rpc.RpcDelegate;

/**
 * This is the actual implementation of the RPC functions that will be called by the client/designer.
 * The @RunsOnClient annotation is needed for <b>any</b> RPC function that will be allowed to be invoked by Vision
 * clients.
 * If you do not have a custom client permission ID registered with the rest of the system, use the special UNRESTRICTED
 * value, as below.
 */
@RpcDelegate.RunsOnClient(clientPermissionId = ClientPermissionsConstants.UNRESTRICTED)
public class RpcFunctionsImpl implements RpcFunctions {

    public RpcFunctionsImpl() {
    }

    @Override
    public void publishMessage(String topic, String payload, int qos) {
        try {
            GatewayHook instance = GatewayHook.getInstance();
            if (instance != null) {
                instance.publishMessageWithQos(topic, payload, qos);
            } else {
                throw new RuntimeException("MQTT Gateway Hook not initialized");
            }
        } catch (Exception e) {
            GatewayHook.getLogger().error("Error publishing MQTT message", e);
            throw e;  // Re-throw so client sees it
        }
    }
}

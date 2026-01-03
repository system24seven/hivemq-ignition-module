package com.system24seven.ignition.hivemqtt;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.system24seven.ignition.hivemqtt.client.ClientScriptModule;

public class DesignerHook extends AbstractDesignerModuleHook {

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        manager.addScriptModule(
            "system.mqtt",
            new ClientScriptModule(),
            new PropertiesFileDocProvider()
        );
    }

}

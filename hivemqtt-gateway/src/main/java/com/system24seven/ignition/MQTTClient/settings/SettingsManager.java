package com.system24seven.ignition.MQTTClient.settings;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.models.ConfigCategory;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.system24seven.ignition.MQTTClient.records.MQSettingsRecord;
import org.apache.log4j.Logger;
import com.system24seven.ignition.MQTTClient.GatewayHook;

public class SettingsManager {

    private final Logger logger = GatewayHook.getLogger(getClass());

    public static final List<ConfigCategory> ConfigCategories = List.of(
            SettingsCategory.CONFIG_CATEGORY
    );

    public static final List<IConfigTab> ConfigPanels = List.of(
            MQSettingsPage.CONFIG_ENTRY
    );

    //public final List<IConnectSettings> SettingsBrokers = new ArrayList<>();

    public SettingsManager(GatewayContext context, GatewayHook gatewayHook) throws Exception {
        SettingsCategory.setup(gatewayHook);
        MQSettingsPage.setup(gatewayHook);

        try {
            MQSettingsRecord.updateSchema(context);
        } catch (SQLException e) {
            throw new Exception("Error updating configuration schema.", e);
        }

        try {
            var settingsRecord = context.getLocalPersistenceInterface().createNew(MQSettingsRecord.META);
            settingsRecord.setId(0L);
            MQSettingsRecord.setDefaults(settingsRecord);
            context.getSchemaUpdater().ensureRecordExists(settingsRecord);
        } catch (Exception e) {
            throw new Exception("Error initializing configuration.", e);
        }

        //settingsBroker = context.getLocalPersistenceInterface().find(MQSettingsRecord.META, 0L);
    }

    public void startup() {

    }

    public void shutdown() {
        SettingsCategory.shutdown();
        MQSettingsPage.shutdown();
    }
}

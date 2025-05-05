package com.system24seven.ignition.MQTTClient.settings;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.web.components.RecordEditForm;
import com.inductiveautomation.ignition.gateway.web.models.DefaultConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;
import com.system24seven.ignition.MQTTClient.GatewayHook;
import com.system24seven.ignition.MQTTClient.records.MQSettingsRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.web.components.RecordActionTable;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.Application;

/**
 * MQSettings extends {@link RecordActionTable} to use the gateway's built in UI for editing {@link PersistentRecord}s
 */
public class MQSettingsPage extends RecordEditForm {

    public static final String MENU_LOCATION_KEY = MQSettingsPage.class.getSimpleName();

    public static final String BUNDLE_FILE_NAME = MQSettingsPage.class.getSimpleName();

    public static final String BUNDLE_PREFIX = MQSettingsPage.class.getSimpleName();

    public static final IConfigTab CONFIG_ENTRY = DefaultConfigTab.builder()
            .category(GatewayHook.CONFIG_CATEGORY)
            .name(MQSettingsPage.MENU_LOCATION_KEY)
            .i18n(BUNDLE_PREFIX + ".nav.settings.title")
            .page(MQSettingsPage.class)
            .terms("Settings")
            .build();

    public static final Pair<String, String> MENU_LOCATION =
            Pair.of(GatewayHook.CONFIG_CATEGORY.getName(), MENU_LOCATION_KEY);

    public MQSettingsPage(IConfigPage configPage) {
          super(configPage, null, new LenientResourceModel(BUNDLE_PREFIX + ".nav.settings.panelTitle"),
                  ((IgnitionWebApp) Application.get()).getContext().getPersistenceInterface().find(MQSettingsRecord.META, 0L)
          );
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return MENU_LOCATION;
    }

    public static void setup(GatewayHook gatewayHook) {
        BundleUtil.get().addBundle(BUNDLE_PREFIX, gatewayHook.getClass(), BUNDLE_FILE_NAME);
    }

    public static void shutdown() {
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);
    }
}

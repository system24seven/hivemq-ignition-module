package com.system24seven.ignition.MQTTClient.settings;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.web.models.ConfigCategory;
import com.system24seven.ignition.MQTTClient.GatewayHook;

public class SettingsCategory {

    public static final String CATEGORY_NAME = "MQTT-Client";

    public static final String BUNDLE_FILE_NAME = SettingsCategory.class.getSimpleName();

    public static final String BUNDLE_PREFIX = SettingsCategory.class.getSimpleName();

    public static final ConfigCategory CONFIG_CATEGORY =
            new ConfigCategory(CATEGORY_NAME, BUNDLE_PREFIX + ".nav.header", 700);

    public static void setup(GatewayHook gatewayHook) {
        BundleUtil.get().addBundle(BUNDLE_PREFIX, gatewayHook.getClass(), BUNDLE_FILE_NAME);
    }

    public static void shutdown() {
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);
    }
}

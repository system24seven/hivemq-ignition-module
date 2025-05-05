package com.system24seven.ignition.MQTTClient.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.EncodedStringField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.components.editors.PasswordEditorSource;
import com.inductiveautomation.ignition.gateway.localdb.persistence.BooleanField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IntField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.Category;
import simpleorm.dataset.SFieldFlags;

import java.sql.SQLException;

/**
 * Filename: MQSettingsRecord
 * Author: David Stone
 * Created on: 21/4/25
 * Project: hivemqtt
 */
public class MQSettingsRecord extends PersistentRecord {

    public static final RecordMeta<MQSettingsRecord> META = new RecordMeta<MQSettingsRecord>(
            MQSettingsRecord.class, "MQSettingsRecord").setNounKey("MQSettingsRecord.Noun").setNounPluralKey(
            "MQSettingsRecord.Noun.Plural");

    public static final IdentityField Id = new IdentityField(META);

    //MQTT Broker Settings
    public static final StringField MQHostname = new StringField(META, "BrokerHostname", SFieldFlags.SMANDATORY);
    public static final BooleanField MQTlsEnable = new BooleanField(META, "EnableTls").setDefault(false);
    public static final IntField MQHostPort = new IntField(META, "BrokerPort", SFieldFlags.SMANDATORY);
    public static final StringField MQUsername = new StringField(META, "BrokerUsername");
    public static final EncodedStringField MQPassword = new EncodedStringField(META, "BrokerPassword");
    static {
        MQPassword.getFormMeta().setEditorSource(PasswordEditorSource.getSharedInstance());
    }
    public static final StringField TopicName = new StringField(META, "TopicName").setDefault("#");;
    // create categories for our record entries, getting titles from the MQSettingsRecord.properties, and
    // ordering through integer ranking
    static final Category Broker = new Category("MQSettingsRecord.Category.Broker", 1000).include(MQHostname, MQHostPort,TopicName);
    static final Category Security = new Category("MQSettingsRecord.Category.Security", 1001).include(MQUsername, MQPassword,MQTlsEnable);

    public static void updateSchema(GatewayContext context) throws SQLException {
        context.getSchemaUpdater().updatePersistentRecords(META);
    }

    public void setId(Long id) {
        setLong(Id, id);
    }

    public Long getId() {
        return getLong(Id);
    }

    public void setTopicName(String topicName) {
        setString(TopicName, topicName);
    }

    public String getTopicName() {
        return getString(TopicName);
    }
    public void setMQTlsEnable(Boolean enable){
        setBoolean(MQTlsEnable, enable);
    }

    public boolean getMQTlsEnable() {
        return getBoolean(MQTlsEnable);
    }

    public void setMQHostname(String hostname) {
        setString(MQHostname, hostname);
    }

    public String getMQHostname() {
        return getString(MQHostname);
    }

    public void setMQHostPort(int port) {
        setInt(MQHostPort, port);
    }

    public int getMQHostPort() {
        return getInt(MQHostPort);
    }

    public void setMQUsername(String username) {
        setString(MQUsername, username);
    }
    public String getMQUsername() {
        return getString(MQUsername);
    }
    public void setMQPassword(String password) {
        setString(MQPassword, password);
    }
    public String getMQPassword() {
        return getString(MQPassword);
    }

    public static void setDefaults(MQSettingsRecord settingsRecord) {
        settingsRecord.setMQHostname("");
        settingsRecord.setMQHostPort(1883);
        settingsRecord.setMQUsername("");
        settingsRecord.setMQPassword("");
        settingsRecord.setMQTlsEnable(false);
        settingsRecord.setTopicName("#");
    }

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
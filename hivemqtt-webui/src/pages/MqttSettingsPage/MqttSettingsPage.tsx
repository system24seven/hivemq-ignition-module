import React, { useEffect, useState } from "react";
import {
  Alert,
  AlertStates,
  ButtonColorClasses,
  Card,
  Checkbox,
  CircularProgress,
  FormLabel,
  PageHeader,
  TextInput,
} from "@inductiveautomation/ignition-web-ui";

interface MqttSettings {
  mqHostname: string;
  mqHostPort: number;
  mqUsername: string;
  mqPassword: string;
  mqTopic: string;
  mqTlsEnable: boolean;
}

const MqttSettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<MqttSettings>({
    mqHostname: "192.168.0.10",
    mqHostPort: 1883,
    mqUsername: "",
    mqPassword: "",
    mqTopic: "",
    mqTlsEnable: false,
  });
  let [cachedCsrfToken] = useState<string | null>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<{
    connected: boolean;
    loading: boolean;
  }>({
    connected: false,
    loading: true,
  });

  useEffect(() => {
    loadSettings();
    const interval = setInterval(loadConnectionStatus, 1000);
    return () => clearInterval(interval);
  }, []);

  const loadSettings = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch("/data/hivemqtt/api/settings");
      if (!response.ok) {
        throw new Error("Failed to load settings");
      }

      const data = await response.json();
      setSettings(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load settings");
    } finally {
      setLoading(false);
    }
  };

  const loadConnectionStatus = async () => {
    try {
      const response = await fetch("/data/hivemqtt/api/mqttStatus");
      if (!response.ok) {
        throw new Error("Failed to load connection status");
      }
      const data = await response.json();
      setConnectionStatus({
        connected: data.connected === "true" || data.connected === true,
        loading: false,
      });
    } catch (err) {
      console.error("Failed to load MQTT status:", err);
      setConnectionStatus({ connected: false, loading: false });
    }
  };

  async function getCsrfToken(): Promise<string | null> {
    if (cachedCsrfToken) {
      return cachedCsrfToken;
    }
    try {
      const response = await fetch("/data/app/session");
      if (response.ok) {
        const data = await response.json();
        cachedCsrfToken = data.csrfToken || null;
        return cachedCsrfToken;
      }
    } catch (error) {
      console.error("Failed to fetch CSRF token:", error);
    }
    return null;
  }

  const handleSave = async () => {
    try {
      setSaving(true);
      setError(null);
      setSuccess(false);

      const response = await fetch("/data/hivemqtt/api/settings", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": (await getCsrfToken()) || "",
        },
        credentials: "include",
        body: JSON.stringify(settings),
      });

      if (!response.ok) {
        throw new Error("Failed to save settings");
      }

      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save settings");
    } finally {
      setSaving(false);
    }
  };

  const handleChange = (field: keyof MqttSettings, value: any) => {
    setSettings((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  if (loading) {
    return (
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "400px",
        }}
      >
        <CircularProgress />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        id="mqtt-settings-header"
        pageTitle="MQTT Tag Driver Settings"
        actionButtons={[
          {
            children: saving ? "Saving..." : "Save Changes",
            colorClass: ButtonColorClasses.PRIMARY,
            onClick: handleSave,
            disabled: saving,
          },
        ]}
      />

      <div style={{ maxWidth: "62.5rem", padding: "24px 24px 0 24px" }}>
        {connectionStatus.loading ? (
          <Alert
            state={AlertStates.INFO}
            title="MQTT Connection"
            description="Checking connection status..."
          ></Alert>
        ) : connectionStatus.connected ? (
          <Alert
            state={AlertStates.SUCCESS}
            title="MQTT Connection"
            description={"Connected to MQTT Broker"}
          ></Alert>
        ) : (
          <Alert
            state={AlertStates.ERROR}
            title="MQTT Connection"
            description="Disconnected from MQTT Broker. Check your connection settings."
          ></Alert>
        )}
      </div>

      <div
        style={{
          maxWidth: "62.5rem",
          gap: "1rem",
          padding: "24px",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {error && (
          <Alert
            state={AlertStates.ERROR}
            style={{ marginBottom: "16px" }}
            title={"Error"}
            description={error}
          ></Alert>
        )}

        {success && (
          <Alert
            state={AlertStates.SUCCESS}
            style={{ marginBottom: "16px" }}
            title={"Success"}
            description="Settings saved successfully!"
          ></Alert>
        )}

        <Card title={"MQTT TAG DRIVER"}>
          <p>
            The MQTT Tag Driver subscribes to a simple MQTT Broker, Creates a
            Tag Provider in Ignition called [MQTT-Client] and mirrors MQTT
            topics as tags.
          </p>
        </Card>

        <Card title="CONNECTION">
          <FormLabel htmlFor={"mqHostname"} label={"MQTT Broker IP"}>
            <TextInput
              label="Broker IP"
              value={settings.mqHostname}
              id="mqHostname"
              onChange={(e) => handleChange("mqHostname", e.target.value)}
              //helperText="MQTT Broker IP or Hostname"
              required
              style={{ marginBottom: "1.5rem" }}
            />
          </FormLabel>

          <FormLabel htmlFor={"mqHostPort"} label={"MQTT Broker Port"}>
            <TextInput
              label="Broker Port"
              type="number"
              id="mqHostPort"
              value={settings.mqHostPort.toString()}
              onChange={(e) =>
                handleChange("mqHostPort", parseInt(e.target.value) || 1883)
              }
              //="MQTT Broker TCP Port (0-65535)"
              required
              inputProps={{ min: 0, max: 65535 }}
              style={{ marginBottom: "1.5rem" }}
            />
          </FormLabel>

          <FormLabel htmlFor={"mqTopic"} label={"MQTT Topic"}>
            <TextInput
              label="Topic"
              type="text"
              id="mqTopic"
              value={settings.mqTopic}
              onChange={(e) => handleChange("mqTopic", e.target.value || "#")}
              //="MQTT Broker TCP Port (0-65535)"
              required
            />
          </FormLabel>
        </Card>

        <Card title="AUTHENTICATION">
          <FormLabel htmlFor={"mqUsername"} label={"MQTT Username"}>
            <TextInput
              label="Username"
              id="mqUsername"
              style={{ marginBottom: "1.5rem" }}
              value={settings.mqUsername}
              onChange={(e) => handleChange("mqUsername", e.target.value)}
            />
          </FormLabel>

          <FormLabel htmlFor={"mqPassword"} label={"MQTT Password"}>
            <TextInput
              id="mqPassword"
              label="Password"
              type="password"
              value={settings.mqPassword}
              onChange={(e) => handleChange("mqPassword", e.target.value)}
            />
          </FormLabel>
        </Card>

        <Card title="SECURITY">
          <FormLabel htmlFor={"mqTlsEnable"} label="Enable TLS">
            <Checkbox
              id="mqTlsEnable"
              defaultValue={settings.mqTlsEnable}
              onChange={(e, checked) => handleChange("mqTlsEnable", checked)}
            />
          </FormLabel>
        </Card>
      </div>
    </div>
  );
};

export default MqttSettingsPage;

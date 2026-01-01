import React from "react";
import { Provider } from "react-redux";
import store from "../../store";
import MqttSettingsPage from "../MqttSettingsPage";

const RootPage = () => {
  return (
    <Provider store={store}>
      <MqttSettingsPage />
    </Provider>
  );
};

export default RootPage;

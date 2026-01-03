package com.system24seven.ignition.hivemqtt.routes;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.dataroutes.AccessControlStrategy;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.system24seven.ignition.hivemqtt.GatewayHook;
import com.system24seven.ignition.hivemqtt.HiveMqttModuleSettingsResource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

public final class apiEndpoints {
    private static Logger logger = GatewayHook.getLogger();
    private static HiveMqttModuleSettingsResource settingsResource;

    public static void mountRoutes(RouteGroup routes, GatewayHook hook) {
        logger.debug("=== MOUNTING ROUTE HANDLERS ===");
        settingsResource = hook.getSettings();

        routes
                .newRoute("/api/settings")
                .handler(
                        (ctx, response) -> {
                            HttpServletRequest request = ctx.getRequest();
                            logger.debug(
                                    "API handler invoked: " + request.getMethod() + " " + request.getRequestURI());
                            try {
                                response.setContentType("application/json");
                                // logger.info("Request: " + request.getMethod());

                                if ("GET".equals(request.getMethod())) {
                                    JsonObject json = new JsonObject();
                                    json.addProperty("mqHostname", settingsResource.mqHostname());
                                    json.addProperty("mqHostPort", settingsResource.mqHostPort());
                                    json.addProperty("mqUsername", settingsResource.mqUsername());
                                    json.addProperty("mqPassword", settingsResource.mqPassword());
                                    json.addProperty("mqTopic", settingsResource.mqTopic());
                                    json.addProperty("mqTlsEnable", settingsResource.mqTlsEnable());

                                    response.getWriter().write(json.toString());
                                } else {
                                    response.setStatus(405);
                                    response.getWriter().write("{\"error\":\"Method not allowed\"}");
                                }

                            } catch (Exception e) {
                                logger.error("Error in API handler", e);
                                response.setStatus(500);
                                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                            }

                            return null; // or return response if needed
                        })
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes
                .newRoute("/api/settings")
                .handler(
                        (ctx, response) -> {
                            HttpServletRequest request = ctx.getRequest();
                            logger.debug(
                                    "API handler invoked: " + request.getMethod() + " " + request.getRequestURI());

                            try {
                                response.setContentType("application/json");
                                // logger.info("Request: " + request.getMethod());

                                if ("POST".equals(request.getMethod())) {
                                    String body =
                                            new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                                    settingsResource =
                                            new HiveMqttModuleSettingsResource(
                                                    json.has("mqHostname") ? json.get("mqHostname").getAsString() : "",
                                                    json.has("mqHostPort") ? json.get("mqHostPort").getAsInt() : 1883,
                                                    json.has("mqUsername") ? json.get("mqUsername").getAsString() : "",
                                                    json.has("mqPassword") ? json.get("mqPassword").getAsString() : "",
                                                    json.has("mqTopic") ? json.get("mqTopic").getAsString() : "",
                                                    json.has("mqTlsEnable") && json.get("mqTlsEnable").getAsBoolean());

                                    hook.updateSettings(settingsResource);

                                    response.getWriter().write("{\"success\":true}");
                                    logger.info("Settings saved successfully");
                                }
                            } catch (Exception e) {
                                logger.error("Error in API handler", e);
                                response.setStatus(500);
                                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                            }

                            return null; // or return response if needed
                        })
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();
    }
}

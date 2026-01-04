package com.system24seven.ignition.hivemqtt.routes;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.dataroutes.*;
import com.system24seven.ignition.hivemqtt.GatewayHook;
import com.system24seven.ignition.hivemqtt.HiveMqttModuleSettingsResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class apiEndpoints {
    private static final Logger logger = GatewayHook.getLogger();
    private static HiveMqttModuleSettingsResource settingsResource;

  public static void mountRoutes(RouteGroup routes,  GatewayHook hook) {
    logger.debug("=== MOUNTING ROUTE HANDLERS ===");
    settingsResource = hook.getSettings();

    routes
        .newRoute("/api/settings")
        .type(RouteGroup.TYPE_JSON)
        .handler(apiEndpoints::getSettings)
        .method(HttpMethod.GET)
        .requirePermission(PermissionType.READ)
        .mount();

    routes
        .newRoute("/api/mqttStatus")
        .type(RouteGroup.TYPE_JSON)
        .handler((ctx, response) -> getStatus(hook, ctx, response))
        .method(HttpMethod.GET)
        .accessControl(AccessControlStrategy.OPEN_ROUTE)
        .mount();
    routes
        .newRoute("/api/settings")
        .type(RouteGroup.TYPE_JSON)
        .handler((ctx, response) -> putSettings(hook,ctx, response))
        .method(HttpMethod.POST)
        .requirePermission(PermissionType.WRITE)
        .mount();
    }

    @Nullable
    private static Object getSettings(RequestContext ctx, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ctx.getRequest();
        logger.debug(
            "API handler invoked: " + request.getMethod() + " " + request.getRequestURI());
        try {
          response.setContentType("application/json");

                          JsonObject json = new JsonObject();
                          json.addProperty("mqHostname", settingsResource.mqHostname());
                          json.addProperty("mqHostPort", settingsResource.mqHostPort());
                          json.addProperty("mqUsername", settingsResource.mqUsername());
                          json.addProperty("mqPassword", settingsResource.mqPassword());
                          json.addProperty("mqTopic", settingsResource.mqTopic());
                          json.addProperty("mqTlsEnable", settingsResource.mqTlsEnable());

                          response.getWriter().write(json.toString());

        } catch (Exception e) {
          logger.error("Error in API handler", e);
          response.setStatus(500);
          response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }

        return null;
    }

    @Nullable
    private static Object getStatus(GatewayHook hook, RequestContext ctx, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ctx.getRequest();
        try {
          response.setContentType("application/json");

            if ("GET".equals(request.getMethod())) {
                JsonObject json = new JsonObject();
                json.addProperty("connected", hook.getMqttStatus());
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
        return null;
    }

    @Nullable
    private static RouteHandler putSettings(GatewayHook hook, RequestContext ctx, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ctx.getRequest();
        try {
            response.setContentType("application/json");
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
        } catch (Exception e) {
            logger.error("Error in API handler", e);
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
        return null;
    }
}

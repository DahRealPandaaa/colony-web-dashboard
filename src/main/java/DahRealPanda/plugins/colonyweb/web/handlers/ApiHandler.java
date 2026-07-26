package DahRealPanda.plugins.colonyweb.web.handlers;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.auth.WebUser;
import DahRealPanda.plugins.colonyweb.colony.ColonyCache;
import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the read-only colony API from the cached scan results.
 *
 * <pre>
 * /api/colonies                            colonies this player may see
 * /api/colony/{id}                         buildings, builders, work orders, warehouse, stats
 * /api/colony/{id}/citizens                citizen roster
 * /api/colony/{id}/citizen/{citizenId}     one citizen plus their inventory
 * /api/colony/{id}/research                research branches and progress
 * /api/colony/{id}/combat                  raid status, guards, guard posts
 * </pre>
 *
 * <p>Every route is scoped to the signed-in player: {@code /api/colonies} only lists colonies
 * they belong to, and the per-colony routes answer 403 for anything else.</p>
 */
public final class ApiHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ColonyCache cache;
    private final AuthService auth;

    public ApiHandler(ColonyCache cache, AuthService auth) {
        this.cache = cache;
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonUtil.sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (RequestAuth.rejectUnauthenticated(auth, exchange)) {
                return;
            }
            WebUser user = RequestAuth.user(auth, exchange).orElse(null);

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/colonies")) {
                JsonUtil.sendJson(exchange, 200, visibleColonies(user));
                return;
            }
            if (path.startsWith("/api/colony/")) {
                routeColony(exchange, user, path.substring("/api/colony/".length()));
                return;
            }
            JsonUtil.sendError(exchange, 404, "Not Found");
        } catch (Exception e) {
            LOGGER.debug("{} api handler error", ColonyWeb.LOG, e);
            safeError(exchange);
        }
    }

    /** Only the colonies this player belongs to (all of them for operators). */
    private List<ColonySummary> visibleColonies(WebUser user) {
        if (!auth.enabled()) {
            return cache.summaries();
        }
        List<ColonySummary> visible = new ArrayList<>();
        for (ColonySummary summary : cache.summaries()) {
            if (auth.canAccess(user, summary.id)) {
                visible.add(summary);
            }
        }
        return visible;
    }

    /** Dispatch the part of the path after {@code /api/colony/}. */
    private void routeColony(HttpExchange exchange, WebUser user, String rest) throws IOException {
        String[] parts = rest.split("/");
        Integer colonyId = parseId(parts.length > 0 ? parts[0] : null);
        if (colonyId == null) {
            JsonUtil.sendError(exchange, 400, "Invalid colony id");
            return;
        }
        if (!auth.canAccess(user, colonyId)) {
            // Deliberately the same answer whether or not the colony exists, so this cannot be
            // used to enumerate colonies the player is not a member of.
            JsonUtil.sendError(exchange, 403, "You do not have access to that colony.");
            return;
        }
        String section = parts.length > 1 ? parts[1] : "";

        switch (section) {
            case "" -> respond(exchange, cache.snapshot(colonyId), "Unknown colony");
            case "citizens" -> respond(exchange, cache.citizens(colonyId), "Unknown colony");
            case "research" -> respond(exchange, cache.research(colonyId), "No research data yet");
            case "combat" -> respond(exchange, cache.combat(colonyId), "No combat data yet");
            case "citizen" -> citizen(exchange, colonyId, parseId(parts.length > 2 ? parts[2] : null));
            default -> JsonUtil.sendError(exchange, 404, "Not Found");
        }
    }

    /** One citizen plus their inventory, which is cached separately to keep the list small. */
    private void citizen(HttpExchange exchange, int colonyId, Integer citizenId) throws IOException {
        if (citizenId == null) {
            JsonUtil.sendError(exchange, 400, "Invalid citizen id");
            return;
        }
        Optional<CitizenInfo> citizen = cache.citizen(colonyId, citizenId);
        if (citizen.isEmpty()) {
            JsonUtil.sendError(exchange, 404, "Unknown citizen");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("citizen", citizen.get());
        body.put("inventory", cache.inventory(colonyId, citizenId));
        body.put("equipment", cache.equipment(colonyId, citizenId));
        JsonUtil.sendJson(exchange, 200, body);
    }

    private void respond(HttpExchange exchange, Optional<?> body, String missing) throws IOException {
        if (body.isPresent()) {
            JsonUtil.sendJson(exchange, 200, body.get());
        } else {
            JsonUtil.sendError(exchange, 404, missing);
        }
    }

    private static Integer parseId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void safeError(HttpExchange exchange) {
        try {
            JsonUtil.sendError(exchange, 500, "Internal Server Error");
        } catch (IOException ignored) {
            // no-op
        }
    }
}

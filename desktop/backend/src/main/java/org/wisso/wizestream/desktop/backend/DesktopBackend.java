package org.wisso.wizestream.desktop.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.schabi.newpipe.sync.DesktopSyncService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DesktopBackend implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExtractorFacade extractor = new ExtractorFacade();
    private final DesktopDatabase database;
    private final DesktopSyncService sync;

    private DesktopBackend(final Path dataDirectory) throws Exception {
        database = new DesktopDatabase(dataDirectory);
        sync = new DesktopSyncService(database.connection(), "WizeStream Desktop");
        sync.start();
    }

    public static void main(final String[] args) throws Exception {
        final Path dataDirectory = dataDirectory(args);
        try (DesktopBackend backend = new DesktopBackend(dataDirectory);
             BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) output.println(backend.handle(line));
        }
    }

    private String handle(final String line) {
        Integer id = null;
        try {
            final JsonNode request = JSON.readTree(line);
            id = requiredInt(request, "id");
            final String method = requiredText(request, "method");
            final JsonNode params = request.path("params");
            final Object result = switch (method) {
                case "health" -> Map.of("status", "ok", "apiVersion", 1, "extractor", "WizeStreamExtractor");
                case "services.list" -> extractor.services();
                case "search" -> extractor.search(requiredInt(params, "serviceId"), requiredText(params, "query"));
                case "stream.resolve" -> extractor.resolve(requiredText(params, "url"));
                case "library.summary" -> database.summary();
                case "sync.status" -> sync.status();
                case "sync.invitation" -> Map.of("pairingCode", sync.createPairingCode());
                case "sync.pair" -> sync.pair(requiredText(params, "pairingCode"));
                default -> throw new IllegalArgumentException("Unknown backend method");
            };
            final ObjectNode response = JSON.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("result", JSON.valueToTree(result));
            return JSON.writeValueAsString(response);
        } catch (final Exception error) {
            return errorResponse(id, error);
        }
    }

    private String errorResponse(final Integer id, final Exception error) {
        final ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null) response.putNull("id"); else response.put("id", id);
        final ObjectNode body = response.putObject("error");
        body.put("code", error instanceof IllegalArgumentException ? -32602 : -32000);
        body.put("message", safeMessage(error));
        try {
            return JSON.writeValueAsString(response);
        } catch (final JsonProcessingException impossible) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    private static String requiredText(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value.textValue();
    }

    private static int requiredInt(final JsonNode node, final String name) {
        final JsonNode value = node.path(name);
        if (!value.canConvertToInt()) throw new IllegalArgumentException("Missing " + name);
        return value.intValue();
    }

    private static String safeMessage(final Exception error) {
        final String value = error.getMessage();
        if (value == null || value.isBlank()) return error.getClass().getSimpleName();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static Path dataDirectory(final String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if ("--data-dir".equals(args[index])) return Path.of(args[index + 1]).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("--data-dir is required");
    }

    @Override
    public void close() throws Exception {
        sync.stop();
        database.close();
    }
}

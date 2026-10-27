package com.demo.upimesh.web;

import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.service.BridgeIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The same bridge upload as /api/bridge/ingest, written as a servlet.
 * doPost reads a MeshPacket JSON body. doGet only describes how to call it.
 */
@Component
public class BridgeIngestServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestServlet.class);

    private final BridgeIngestionService bridge;
    private final ObjectMapper json;

    public BridgeIngestServlet(BridgeIngestionService bridge, ObjectMapper json) {
        this.bridge = bridge;
        this.json = json;
    }

    @Override
    public void init() {
        log.info("Bridge ingest servlet started");
    }

    @Override
    public void destroy() {
        log.info("Bridge ingest servlet stopped");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().println("""
                POST a MeshPacket JSON body to this URL.
                Optional headers: X-Bridge-Node-Id, X-Hop-Count.
                Example fields: packetId, ttl, createdAt, ciphertext.
                """);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        try {
            MeshPacket packet = json.readValue(request.getInputStream(), MeshPacket.class);
            String bridgeNodeId = headerOrDefault(request, "X-Bridge-Node-Id", "servlet-bridge");
            int hopCount = parseHopCount(request.getHeader("X-Hop-Count"));
            BridgeIngestionService.IngestResult result = bridge.ingest(packet, bridgeNodeId, hopCount);
            response.setStatus(HttpServletResponse.SC_OK);
            json.writeValue(response.getOutputStream(), result);
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> body = new LinkedHashMap<>();
            body.put("outcome", "INVALID");
            body.put("reason", ex.getMessage() == null ? "bad_request" : ex.getMessage());
            json.writeValue(response.getOutputStream(), body);
        }
    }

    private static String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int parseHopCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

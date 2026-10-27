package com.demo.upimesh.web;

import com.demo.upimesh.jdbc.JdbcLedger;
import com.demo.upimesh.model.MeshDevice;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.MeshSimulatorService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web entry for the ledger. doGet paints the JSP. doPost runs one demo action.
 */
@Component
public class LedgerServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LedgerServlet.class);

    private final JdbcLedger ledger;
    private final DemoService demo;
    private final MeshSimulatorService mesh;
    private final BridgeIngestionService bridge;
    private final IdempotencyService idempotency;

    public LedgerServlet(JdbcLedger ledger, DemoService demo, MeshSimulatorService mesh,
                         BridgeIngestionService bridge, IdempotencyService idempotency) {
        this.ledger = ledger;
        this.demo = demo;
        this.mesh = mesh;
        this.bridge = bridge;
        this.idempotency = idempotency;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        log.info("Ledger servlet started");
    }

    @Override
    public void destroy() {
        log.info("Ledger servlet stopped");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(true);
        int visits = nextVisit(session);
        try {
            LedgerPage pageBean = new LedgerPage();
            pageBean.setVisitCount(visits);
            Object last = session.getAttribute("lastOutcome");
            pageBean.setLastOutcome(last == null ? "" : String.valueOf(last));
            request.setAttribute("pageBean", pageBean);
            request.setAttribute("accounts", ledger.listAccounts());
            request.setAttribute("transactions", ledger.listRecentTransactions());
            request.setAttribute("devices", deviceRows());
            request.setAttribute("cacheSize", idempotency.size());
            request.setAttribute("message", request.getParameter("message"));
            request.getRequestDispatcher("/WEB-INF/jsp/ledger.jsp").forward(request, response);
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");
        String message;
        try {
            if ("settle".equals(action)) {
                message = settle(request);
            } else if ("gossip".equals(action)) {
                MeshSimulatorService.GossipResult result = mesh.gossipOnce();
                message = "Gossip transfers: " + result.transfers();
            } else if ("flush".equals(action)) {
                message = processUploads();
            } else if ("reset".equals(action)) {
                mesh.resetMesh();
                idempotency.clear();
                message = "Mesh packets and the idempotency cache were cleared. Balances were kept.";
            } else {
                message = "Unknown action";
            }
        } catch (Exception ex) {
            message = ex.getMessage() == null ? "Request failed" : ex.getMessage();
        }
        request.getSession(true).setAttribute("lastOutcome", message);
        response.sendRedirect(request.getContextPath() + "/ledger?message="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String settle(HttpServletRequest request) throws Exception {
        String sender = request.getParameter("senderVpa");
        String receiver = request.getParameter("receiverVpa");
        BigDecimal amount = new BigDecimal(request.getParameter("amount").trim());
        String pin = request.getParameter("pin");
        MeshPacket packet = demo.createPacket(sender, receiver, amount, pin, 5);
        String phone = "phone-alice";
        mesh.inject(phone, packet);
        return "Packet injected at " + phone + ". Run gossip, then flush the bridge. Money moves on flush.";
    }

    /**
     * One pass over every packet a bridge is holding. A failure on one packet
     * does not stop the rest of the batch.
     */
    private String processUploads() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();
        int settled = 0;
        int rejected = 0;
        int duplicates = 0;
        int invalid = 0;
        for (MeshSimulatorService.BridgeUpload upload : uploads) {
            try {
                int hops = Math.max(0, 5 - upload.packet().getTtl());
                BridgeIngestionService.IngestResult result =
                        bridge.ingest(upload.packet(), upload.bridgeNodeId(), hops);
                switch (result.outcome()) {
                    case "SETTLED" -> settled++;
                    case "REJECTED" -> rejected++;
                    case "DUPLICATE_DROPPED" -> duplicates++;
                    default -> invalid++;
                }
            } catch (Exception ex) {
                invalid++;
            }
        }
        return "Flush finished. Settled " + settled + ", rejected " + rejected
                + ", duplicates " + duplicates + ", invalid " + invalid + ".";
    }

    private List<Map<String, Object>> deviceRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MeshDevice device : mesh.getDevices()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", device.getDeviceId());
            row.put("online", device.hasInternet());
            row.put("packetCount", device.packetCount());
            rows.add(row);
        }
        return rows;
    }

    private static int nextVisit(HttpSession session) {
        Integer visits = (Integer) session.getAttribute("visitCount");
        if (visits == null) {
            visits = 0;
        }
        visits = visits + 1;
        session.setAttribute("visitCount", visits);
        return visits;
    }
}

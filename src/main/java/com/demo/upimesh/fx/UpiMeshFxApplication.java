package com.demo.upimesh.fx;

import com.demo.upimesh.UpiMeshApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** JavaFX desktop dashboard for the UPI Offline Mesh demo. */
public class UpiMeshFxApplication extends Application {
    private static final String API = "http://localhost:8080/api";
    private static ConfigurableApplicationContext springContext;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final VBox devicesBox = new VBox(8);
    private final TableView<JsonNode> accountsTable = new TableView<>();
    private final TableView<JsonNode> transactionsTable = new TableView<>();
    private final TextArea activityLog = new TextArea();
    private final Label cacheLabel = new Label("Idempotency cache: —");
    private final Label statusLabel = new Label("Ready");
    private ComboBox<String> senderBox;
    private ComboBox<String> receiverBox;
    private TextField amountField;
    private PasswordField pinField;
    private Timeline refreshTimer;

    public static void main(String[] args) {
        // Keep the existing Spring REST backend and replace only its web dashboard.
        springContext = SpringApplication.run(UpiMeshApplication.class, args);
        launch(args);
    }

    @Override public void start(Stage stage) {
        Label title = new Label("📡 UPI Offline Mesh");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Offline payments • Encrypted packets • Bluetooth mesh simulation");
        subtitle.getStyleClass().add("subtitle");

        VBox header = new VBox(5, title, subtitle);
        header.setPadding(new Insets(0, 0, 12, 0));

        VBox root = new VBox(14, buildMenu(), header, buildControls(), buildOverview(), buildTransactions(), buildLog());
        root.setPadding(new Insets(22));
        root.getStyleClass().add("root-pane");
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");

        Scene scene = new Scene(scroll, 1200, 850);
        scene.getStylesheets().add(getClass().getResource("/fx-theme.css").toExternalForm());
        stage.setTitle("UPI Offline Mesh — Desktop Demo");
        stage.setMinWidth(950);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();

        refreshAll();
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> refreshAll()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private MenuBar buildMenu() {
        MenuItem gossip = new MenuItem("Run gossip");
        gossip.setOnAction(e -> runAction("/mesh/gossip", "Gossip round completed"));
        MenuItem flush = new MenuItem("Flush bridges");
        flush.setOnAction(e -> flushBridges());
        MenuItem reset = new MenuItem("Reset mesh");
        reset.setOnAction(e -> resetMesh());
        Menu mesh = new Menu("Mesh", null, gossip, flush, reset);

        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About");
            alert.setHeaderText("UPI Offline Mesh");
            alert.setContentText("A phone encrypts a payment. Nearby phones carry the packet. The one phone with internet uploads it, and the server settles it once.");
            alert.showAndWait();
        });
        Menu help = new Menu("Help", null, about);
        return new MenuBar(mesh, help);
    }

    private VBox buildControls() {
        Label heading = sectionTitle("DEMO FLOW");
        senderBox = new ComboBox<>(FXCollections.observableArrayList("alice@demo", "bob@demo", "carol@demo"));
        senderBox.setValue("alice@demo");
        receiverBox = new ComboBox<>(FXCollections.observableArrayList("bob@demo", "carol@demo", "alice@demo", "dave@demo"));
        receiverBox.setValue("bob@demo");
        amountField = new TextField("500"); amountField.setPrefWidth(100); amountField.setPromptText("Amount ₹");
        pinField = new PasswordField(); pinField.setText("1234"); pinField.setPrefWidth(90); pinField.setPromptText("PIN");

        Button send = primaryButton("📤 Inject Payment");
        send.setOnAction(e -> sendPacket());
        HBox paymentRow = new HBox(9, stepLabel("1"), new Label("Compose payment"), senderBox,
                new Label("→"), receiverBox, new Label("₹"), amountField, new Label("PIN"), pinField, send);
        paymentRow.setAlignment(Pos.CENTER_LEFT);

        Button gossip = secondaryButton("🔄 Run Gossip Round");
        gossip.setOnAction(e -> runAction("/mesh/gossip", "Gossip round completed"));
        Button flush = secondaryButton("📡 Bridge Uploads to Backend");
        flush.setOnAction(e -> flushBridges());
        Button reset = dangerButton("🗑 Reset Mesh + Cache");
        reset.setOnAction(e -> resetMesh());
        HBox actionRow = new HBox(10, stepLabel("2"), new Label("Gossip"), gossip,
                stepLabel("3"), new Label("Bridge reaches internet"), flush, reset);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("status-label");
        VBox card = new VBox(14, heading, paymentRow, new Separator(), actionRow, statusLabel);
        card.getStyleClass().add("card");
        return card;
    }

    private HBox buildOverview() {
        Label devicesTitle = sectionTitle("📱 MESH DEVICES");
        devicesBox.setFillWidth(true);
        VBox devicesCard = new VBox(12, devicesTitle, devicesBox);
        devicesCard.getStyleClass().add("card");
        devicesCard.setPrefWidth(520);

        Label accountsTitle = sectionTitle("🏦 ACCOUNT BALANCES");
        configureTable(accountsTable, new String[][]{{"VPA", "vpa"}, {"Holder", "holderName"}, {"Balance", "balance"}});
        accountsTable.setPrefHeight(240);
        VBox accountsCard = new VBox(12, accountsTitle, accountsTable, cacheLabel);
        accountsCard.getStyleClass().add("card");
        HBox row = new HBox(14, devicesCard, accountsCard);
        HBox.setHgrow(devicesCard, Priority.ALWAYS); HBox.setHgrow(accountsCard, Priority.ALWAYS);
        devicesCard.setMaxWidth(Double.MAX_VALUE); accountsCard.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private VBox buildTransactions() {
        Label title = sectionTitle("📜 TRANSACTION LEDGER");
        configureTable(transactionsTable, new String[][]{
                {"ID", "id"}, {"From", "senderVpa"}, {"To", "receiverVpa"},
                {"Amount", "amount"}, {"Status", "status"}, {"Bridge", "bridgeNodeId"},
                {"Hops", "hopCount"}, {"Settled At", "settledAt"}
        });
        transactionsTable.setPrefHeight(260);
        VBox card = new VBox(12, title, transactionsTable);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildLog() {
        Label title = sectionTitle("🪵 ACTIVITY LOG");
        activityLog.setEditable(false);
        activityLog.setWrapText(true);
        activityLog.setPrefRowCount(7);
        activityLog.getStyleClass().add("activity-log");
        VBox card = new VBox(12, title, activityLog);
        card.getStyleClass().add("card");
        return card;
    }

    private void sendPacket() {
        try {
            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            if (senderBox.getValue().equals(receiverBox.getValue())) throw new IllegalArgumentException("Sender and receiver must be different.");
            if (pinField.getText().isBlank()) throw new IllegalArgumentException("Enter a demo PIN.");
            var body = mapper.createObjectNode();
            body.put("senderVpa", senderBox.getValue());
            body.put("receiverVpa", receiverBox.getValue());
            body.put("amount", amount);
            body.put("pin", pinField.getText());
            body.put("ttl", 5);
            body.put("startDevice", "phone-alice");
            JsonNode result = request("POST", "/demo/send", body.toString());
            log("📤 Packet " + shortId(result.path("packetId").asText()) + " encrypted and injected at " + result.path("injectedAt").asText() + " (TTL " + result.path("ttl").asText() + ")");
            log("Ciphertext preview: " + result.path("ciphertextPreview").asText());
            setStatus("Payment packet injected into the mesh.", false);
            refreshAll();
        } catch (Exception ex) { showError(ex); }
    }

    private void runAction(String path, String message) {
        try {
            JsonNode result = request("POST", path, "{}");
            if (path.endsWith("gossip")) log("🔄 Gossip: " + result.path("transfers").asInt() + " transfer(s); device counts: " + result.path("deviceCounts"));
            else log(message);
            setStatus(message, false); refreshAll();
        } catch (Exception ex) { showError(ex); }
    }

    private void flushBridges() {
        try {
            JsonNode result = request("POST", "/mesh/flush", "{}");
            log("📡 Bridge uploads attempted: " + result.path("uploadsAttempted").asInt());
            for (JsonNode item : result.path("results")) {
                log("  " + item.path("bridgeNode").asText() + " packet " + item.path("packetId").asText() + " → " + item.path("outcome").asText()
                        + (item.path("reason").asText().isBlank() ? "" : " (" + item.path("reason").asText() + ")"));
            }
            setStatus("Bridge upload operation completed.", false); refreshAll();
        } catch (Exception ex) { showError(ex); }
    }

    private void resetMesh() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Clear all mesh packets and the idempotency cache? Account balances and settled transactions are not deleted.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Reset mesh simulation");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        runAction("/mesh/reset", "Mesh and idempotency cache cleared");
        log("🗑 Mesh + idempotency cache cleared.");
    }

    private void refreshAll() {
        try {
            JsonNode mesh = request("GET", "/mesh/state", null);
            devicesBox.getChildren().clear();
            for (JsonNode device : mesh.path("devices")) {
                boolean online = device.path("hasInternet").asBoolean();
                Label name = new Label(device.path("deviceId").asText()); name.getStyleClass().add("device-name");
                Label badge = new Label(online ? "● INTERNET / BRIDGE" : "● OFFLINE");
                badge.getStyleClass().add(online ? "online-badge" : "offline-badge");
                Label count = new Label("Holding " + device.path("packetCount").asInt() + " packet(s)"); count.getStyleClass().add("muted");
                Label ids = new Label();
                List<String> packetIds = new ArrayList<>(); device.path("packetIds").forEach(p -> packetIds.add(p.asText()));
                ids.setText(packetIds.isEmpty() ? "No packets" : String.join("   •   ", packetIds)); ids.getStyleClass().add("packet-ids");
                VBox info = new VBox(5, new HBox(9, name, badge), count, ids);
                info.getParent();
                HBox deviceCard = new HBox(info); deviceCard.getStyleClass().add(online ? "device-card-bridge" : "device-card");
                deviceCard.setPadding(new Insets(10)); deviceCard.setMaxWidth(Double.MAX_VALUE);
                devicesBox.getChildren().add(deviceCard);
            }
            cacheLabel.setText("Idempotency cache size: " + mesh.path("idempotencyCacheSize").asInt());
            JsonNode accounts = request("GET", "/accounts", null);
            accountsTable.setItems(FXCollections.observableArrayList());
            accounts.forEach(accountsTable.getItems()::add);
            JsonNode transactions = request("GET", "/transactions", null);
            transactionsTable.setItems(FXCollections.observableArrayList());
            transactions.forEach(transactionsTable.getItems()::add);
        } catch (Exception ex) { setStatus("Backend connection issue: " + ex.getMessage(), true); }
    }

    private void configureTable(TableView<JsonNode> table, String[][] columns) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No records yet"));
        for (String[] column : columns) {
            TableColumn<JsonNode, String> c = new TableColumn<>(column[0]);
            String key = column[1];
            c.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatValue(data.getValue().path(key).asText("—"), key)));
            c.setMinWidth(key.equals("settledAt") ? 150 : 70);
            table.getColumns().add(c);
        }
    }

    private String shortId(String id) {
        if (id == null || id.isBlank()) return "unknown";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private String formatValue(String value, String key) {
        if (key.equals("balance") || key.equals("amount")) {
            try { return "₹" + new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP); }
            catch (Exception ignored) { return value; }
        }
        if (key.equals("settledAt") && !value.equals("—")) {
            try { return value.replace('T', ' ').replaceAll("\\.\\d+Z$", " UTC").replace("Z", " UTC"); }
            catch (Exception ignored) { return value; }
        }
        return value;
    }

    private JsonNode request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path)).header("Accept", "application/json");
        if (method.equals("POST")) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        else builder.GET();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    }

    private void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        activityLog.setText("[" + time + "] " + message + "\n" + activityLog.getText());
    }
    private void showError(Exception ex) {
        setStatus(ex.getMessage() == null ? "Operation failed" : ex.getMessage(), true);
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("UPI Mesh Error"); alert.setHeaderText("Operation could not be completed"); alert.setContentText(ex.getMessage()); alert.showAndWait();
    }
    private void setStatus(String text, boolean error) {
        statusLabel.setText(text); statusLabel.getStyleClass().removeAll("status-error", "status-ok"); statusLabel.getStyleClass().add(error ? "status-error" : "status-ok");
    }
    private Label sectionTitle(String text) { Label l = new Label(text); l.getStyleClass().add("section-title"); return l; }
    private Label stepLabel(String text) { Label l = new Label(text); l.getStyleClass().add("step-number"); return l; }
    private Button primaryButton(String text) { Button b = new Button(text); b.getStyleClass().add("primary-button"); return b; }
    private Button secondaryButton(String text) { Button b = new Button(text); b.getStyleClass().add("secondary-button"); return b; }
    private Button dangerButton(String text) { Button b = new Button(text); b.getStyleClass().add("danger-button"); return b; }

    @Override public void stop() {
        if (refreshTimer != null) refreshTimer.stop();
        if (springContext != null) springContext.close();
        Platform.exit();
    }
}

package com.demo.upimesh.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/**
 * The person sending money. Builds the payment instruction that will be
 * encrypted and dropped onto that person's phone.
 */
public class Payer {

    private static final String[] VPAS;
    private static final String[] NAMES;
    private static final String[] PHONES;

    static {
        VPAS = new String[] { "alice@demo", "bob@demo", "carol@demo", "dave@demo" };
        NAMES = new String[] { "Alice", "Bob", "Carol", "Dave" };
        PHONES = new String[] { "phone-alice", "phone-stranger1", "phone-stranger2", "phone-stranger3" };
    }

    private final String vpa;
    private final String holderName;
    private final String deviceId;

    public Payer(String vpa, String holderName, String deviceId) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.deviceId = deviceId;
    }

    /** Looks the address up in the tables above. An unknown address still gets a payer. */
    public Payer(String vpa) {
        this(normalize(vpa), nameFor(vpa), phoneFor(vpa));
    }

    public static Payer ofVpa(String vpa) {
        return new Payer(vpa);
    }

    public String getVpa() { return vpa; }
    public String getHolderName() { return holderName; }
    public String getDeviceId() { return deviceId; }

    /**
     * Fill in nonce, timestamp, and PIN hash. The PIN is not checked here;
     * a real bank would compare the hash. This only rejects a payment that is
     * already nonsense before it is encrypted.
     */
    public PaymentInstruction sign(String receiverVpa, BigDecimal amount, String pin) {
        Vpa receiver = new Vpa(receiverVpa);
        if (receiver.text().equalsIgnoreCase(vpa)) {
            throw new IllegalArgumentException("Sender and receiver must be different");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (pin == null || pin.isBlank()) {
            throw new IllegalArgumentException("PIN is required");
        }

        BigDecimal rupees = amount.setScale(2, RoundingMode.HALF_UP);
        return new PaymentInstruction(
                vpa,
                receiver.text(),
                rupees,
                sha256Hex(pin),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );
    }

    private static String normalize(String vpa) {
        return new Vpa(vpa).text();
    }

    private static String nameFor(String vpa) {
        String text = normalize(vpa);
        for (int i = 0; i < VPAS.length; i++) {
            if (VPAS[i].equalsIgnoreCase(text)) {
                return NAMES[i];
            }
        }
        return text.substring(0, text.indexOf('@'));
    }

    private static String phoneFor(String vpa) {
        String text = normalize(vpa);
        for (int i = 0; i < VPAS.length; i++) {
            if (VPAS[i].equalsIgnoreCase(text)) {
                return PHONES[i];
            }
        }
        return "phone-alice";
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

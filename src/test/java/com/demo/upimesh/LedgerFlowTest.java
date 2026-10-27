package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.Payer;
import com.demo.upimesh.model.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerFlowTest {

    @Autowired private DemoService demoService;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private HybridCryptoService crypto;

    @BeforeEach
    void clearCache() {
        idempotency.clear();
    }

    @Test
    void payerRejectsAPaymentToYourself() {
        Payer alice = Payer.ofVpa("alice@demo");
        assertThrows(IllegalArgumentException.class,
                () -> alice.sign("alice@demo", new BigDecimal("10.00"), "1234"));
    }

    @Test
    void ciphertextHashIgnoresNonCanonicalBase64() throws Exception {
        assertEquals(crypto.hashCiphertext("AA=="), crypto.hashCiphertext("AB=="));
    }

    @Test
    void insufficientFundsIsRejectedAndNotSettledAgain() throws Exception {
        BigDecimal before = accounts.findById("dave@demo").orElseThrow().getBalance();
        MeshPacket packet = demoService.createPacket(
                "dave@demo", "alice@demo", new BigDecimal("10000.00"), "1234", 5);

        BridgeIngestionService.IngestResult first = bridge.ingest(packet, "bridge-x", 1);
        BridgeIngestionService.IngestResult second = bridge.ingest(packet, "bridge-y", 1);

        assertEquals("REJECTED", first.outcome());
        assertEquals("insufficient_funds", first.reason());
        assertNotNull(first.transactionId());
        assertEquals("DUPLICATE_DROPPED", second.outcome());
        assertEquals(before, accounts.findById("dave@demo").orElseThrow().getBalance());
        assertTrue(transactions.findById(first.transactionId()).orElseThrow().getStatus().name().equals("REJECTED"));
    }

    @Test
    void unknownReceiverReleasesTheClaim() throws Exception {
        BigDecimal before = accounts.findById("alice@demo").orElseThrow().getBalance();
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "nobody@demo", new BigDecimal("10.00"), "1234", 5);

        BridgeIngestionService.IngestResult first = bridge.ingest(packet, "bridge-x", 1);
        BridgeIngestionService.IngestResult second = bridge.ingest(packet, "bridge-x", 1);

        assertEquals("INVALID", first.outcome());
        assertEquals("Unknown receiver VPA: nobody@demo", first.reason());
        assertEquals("INVALID", second.outcome());
        assertEquals(before, accounts.findById("alice@demo").orElseThrow().getBalance());
    }

    @Test
    void nonCanonicalEncodingOfTheSamePacketSettlesOnce() throws Exception {
        BigDecimal carolBefore = accounts.findById("carol@demo").orElseThrow().getBalance();
        BigDecimal bobBefore = accounts.findById("bob@demo").orElseThrow().getBalance();
        MeshPacket packet = null;
        String alternate = null;
        BigDecimal amount = null;
        for (int rupees = 1; rupees <= 100 && alternate == null; rupees *= 10) {
            amount = new BigDecimal(rupees).setScale(2);
            packet = demoService.createPacket("carol@demo", "bob@demo", amount, "1234", 5);
            alternate = nonCanonical(packet.getCiphertext());
        }
        assertNotNull(alternate, "none of the sample ciphertexts had unused Base64 bits");

        MeshPacket replay = new MeshPacket();
        replay.setPacketId(packet.getPacketId());
        replay.setTtl(packet.getTtl());
        replay.setCreatedAt(packet.getCreatedAt());
        replay.setCiphertext(alternate);

        BridgeIngestionService.IngestResult first = bridge.ingest(packet, "bridge-a", 1);
        BridgeIngestionService.IngestResult second = bridge.ingest(replay, "bridge-b", 1);

        assertEquals("SETTLED", first.outcome());
        assertEquals("DUPLICATE_DROPPED", second.outcome());
        assertEquals(carolBefore.subtract(amount), accounts.findById("carol@demo").orElseThrow().getBalance());
        assertEquals(bobBefore.add(amount), accounts.findById("bob@demo").orElseThrow().getBalance());
    }

    private static String nonCanonical(String base64) {
        byte[] raw = Base64.getDecoder().decode(base64);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        char[] chars = base64.toCharArray();
        int end = base64.indexOf('=');
        if (end < 0) {
            end = chars.length;
        }
        int index = end - 1;
        int pos = alphabet.indexOf(chars[index]);
        if (pos < 0) {
            return null;
        }
        for (int delta = 1; delta < alphabet.length(); delta++) {
            chars[index] = alphabet.charAt((pos + delta) % alphabet.length());
            String candidate = new String(chars);
            try {
                if (Arrays.equals(raw, Base64.getDecoder().decode(candidate))) {
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // try the next character
            }
        }
        return null;
    }
}

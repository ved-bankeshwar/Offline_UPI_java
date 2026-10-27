package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.jdbc.DuplicatePacketException;
import com.demo.upimesh.jdbc.LedgerConflictException;
import com.demo.upimesh.jdbc.PaymentException;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;


@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SettlementService settlement;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        if (packet == null || packet.getCiphertext() == null || packet.getCiphertext().isBlank()) {
            return IngestResult.invalid("?", "missing_ciphertext");
        }

        final String packetHash;
        try {
            packetHash = crypto.hashCiphertext(packet.getCiphertext());
        } catch (Exception e) {
            log.warn("Ciphertext could not be hashed: {}", e.getMessage());
            return IngestResult.invalid("?", "bad_ciphertext");
        }

        if (!idempotency.claim(packetHash)) {
            log.info("DUPLICATE packet {} from bridge {} — dropped", shortHash(packetHash), bridgeNodeId);
            return IngestResult.duplicate(packetHash);
        }

        try {
            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                idempotency.release(packetHash);
                log.warn("Decryption failed for packet {}: {}", shortHash(packetHash), e.getMessage());
                return IngestResult.invalid(packetHash, "decryption_failed");
            }

            if (instruction.getSignedAt() == null) {
                idempotency.release(packetHash);
                return IngestResult.invalid(packetHash, "missing_timestamp");
            }

            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            checkAge(packetHash, ageSeconds);

            Transaction tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            if (tx.getStatus() == Transaction.Status.REJECTED) {
                return IngestResult.rejected(packetHash, tx);
            }
            return IngestResult.settled(packetHash, tx);
        } catch (StalePacketException e) {
            log.warn("Packet {} rejected: {}", shortHash(packetHash), e.getMessage());
            return IngestResult.invalid(packetHash, e.getMessage());
        } catch (DuplicatePacketException e) {
            return IngestResult.duplicate(packetHash);
        } catch (LedgerConflictException e) {
            idempotency.release(packetHash);
            return IngestResult.invalid(packetHash, "settlement_conflict");
        } catch (PaymentException e) {
            idempotency.release(packetHash);
            log.warn("Packet {} rejected: {}", shortHash(packetHash), e.getMessage());
            return IngestResult.invalid(packetHash, e.getMessage());
        } catch (IllegalArgumentException e) {
            idempotency.release(packetHash);
            log.warn("Packet {} rejected: {}", shortHash(packetHash), e.getMessage());
            return IngestResult.invalid(packetHash, e.getMessage());
        } catch (Exception e) {
            idempotency.release(packetHash);
            log.error("Ingestion error: {}", e.getMessage(), e);
            return IngestResult.invalid(packetHash, "internal_error");
        }
    }

    private void checkAge(String packetHash, long ageSeconds) {
        if (ageSeconds > maxAgeSeconds) {
            log.warn("Packet {} too old ({}s), rejected", shortHash(packetHash), ageSeconds);
            throw new StalePacketException("stale_packet");
        }
        if (ageSeconds < -300) {
            throw new StalePacketException("future_dated");
        }
    }

    private static String shortHash(String packetHash) {
        return packetHash.substring(0, Math.min(12, packetHash.length())) + "...";
    }

    public record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, Transaction tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }
        public static IngestResult rejected(String hash, Transaction tx) {
            return new IngestResult("REJECTED", hash, "insufficient_funds", tx.getId());
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
    }
}

package com.demo.upimesh.service;

import com.demo.upimesh.jdbc.JdbcLedger;
import com.demo.upimesh.jdbc.LedgerConflictException;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Moves money by calling the JDBC ledger. A version clash is retried a few
 * times so two different payments from the same account can both land when
 * the balance covers both.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final int ATTEMPTS = 3;

    private final JdbcLedger ledger;

    public SettlementService(JdbcLedger ledger) {
        this.ledger = ledger;
    }

    public Transaction settle(PaymentInstruction instruction, String packetHash,
                              String bridgeNodeId, int hopCount) {
        LedgerConflictException lastConflict = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return ledger.transfer(instruction, packetHash, bridgeNodeId, hopCount);
            } catch (LedgerConflictException ex) {
                lastConflict = ex;
                log.warn("Ledger conflict attempt {}/{} for packet {}", attempt, ATTEMPTS,
                        packetHash.substring(0, Math.min(12, packetHash.length())));
            }
        }
        throw lastConflict;
    }
}

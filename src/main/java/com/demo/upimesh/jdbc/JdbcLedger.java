package com.demo.upimesh.jdbc;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ledger access through JDBC. Debit, credit, and the ledger insert share one
 * connection and either commit together or roll back together.
 */
@Component
public class JdbcLedger {

    private static final Logger log = LoggerFactory.getLogger(JdbcLedger.class);

    private static final String SELECT_ACCOUNT =
            "SELECT vpa, holder_name, balance, version FROM accounts WHERE vpa = ?";
    private static final String UPDATE_BALANCE =
            "UPDATE accounts SET balance = ?, version = ? WHERE vpa = ? AND version = ?";
    private static final String INSERT_TX =
            "INSERT INTO transactions (packet_hash, sender_vpa, receiver_vpa, amount, signed_at, settled_at, bridge_node_id, hop_count, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_TX_ID =
            "SELECT id FROM transactions WHERE packet_hash = ?";
    private static final String LIST_ACCOUNTS =
            "SELECT vpa, holder_name, balance, version FROM accounts ORDER BY vpa";
    private static final String LIST_TX =
            "SELECT id, packet_hash, sender_vpa, receiver_vpa, amount, signed_at, settled_at, bridge_node_id, hop_count, status " +
            "FROM transactions ORDER BY id DESC LIMIT 20";

    private final DataSource dataSource;
    private final Path settlementLog;

    public JdbcLedger(DataSource dataSource,
                      @Value("${upi.mesh.settlement-log:data/ledger/settlements.log}") String settlementLog) {
        this.dataSource = dataSource;
        this.settlementLog = Path.of(settlementLog);
    }

    public Transaction transfer(PaymentInstruction instruction, String packetHash,
                                String bridgeNodeId, int hopCount) {
        BigDecimal amount = instruction.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (instruction.getSenderVpa() == null || instruction.getReceiverVpa() == null) {
            throw new IllegalArgumentException("Sender and receiver are required");
        }
        if (instruction.getSenderVpa().equals(instruction.getReceiverVpa())) {
            throw new IllegalArgumentException("Sender and receiver must be different");
        }

        Connection conn = null;
        boolean committed = false;
        Transaction tx = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Account sender = findAccount(conn, instruction.getSenderVpa());
            Account receiver = findAccount(conn, instruction.getReceiverVpa());
            if (sender == null) {
                throw new UnknownAccountException("Unknown sender VPA: " + instruction.getSenderVpa());
            }
            if (receiver == null) {
                throw new UnknownAccountException("Unknown receiver VPA: " + instruction.getReceiverVpa());
            }

            Transaction.Status status;
            if (sender.getBalance().compareTo(amount) < 0) {
                status = Transaction.Status.REJECTED;
                log.warn("Insufficient balance: {} has {}, tried to send {}",
                        sender.getVpa(), sender.getBalance(), amount);
            } else {
                status = Transaction.Status.SETTLED;
                applyTransfer(conn, sender, receiver, amount);
            }

            tx = insertTransaction(conn, instruction, packetHash, bridgeNodeId, hopCount, amount, status);
            conn.commit();
            committed = true;
        } catch (Exception ex) {
            if (ex instanceof SQLException sql && isUniqueViolation(sql)) {
                throw new DuplicatePacketException(packetHash);
            }
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Ledger update failed", ex);
        } finally {
            // Close here. Object.finalize() is obsolete and is not used.
            if (conn != null) {
                try {
                    if (!committed) {
                        conn.rollback();
                    }
                } catch (SQLException ex) {
                    log.warn("Rollback failed: {}", ex.getMessage());
                }
                try {
                    conn.close();
                } catch (SQLException ex) {
                    log.warn("Could not close the ledger connection: {}", ex.getMessage());
                }
            }
        }

        appendLog(tx);
        log.info("{} {} from {} to {} (packetHash={}, bridge={}, hops={})",
                tx.getStatus(), amount, tx.getSenderVpa(), tx.getReceiverVpa(),
                packetHash.substring(0, Math.min(12, packetHash.length())), bridgeNodeId, hopCount);
        return tx;
    }

    public List<Account> listAccounts() {
        List<Account> accounts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_ACCOUNTS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(readAccount(rs));
            }
            return accounts;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list accounts", ex);
        }
    }

    public List<Transaction> listRecentTransactions() {
        List<Transaction> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_TX);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(readTransaction(rs));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list transactions", ex);
        }
    }

    private void applyTransfer(Connection conn, Account sender, Account receiver, BigDecimal amount) throws SQLException {
        // Smaller VPA first, so two payments lock the rows in the same order.
        boolean senderFirst = sender.getVpa().compareTo(receiver.getVpa()) < 0;
        Account first = senderFirst ? sender : receiver;
        Account second = senderFirst ? receiver : sender;
        BigDecimal firstBalance = first == sender
                ? sender.getBalance().subtract(amount)
                : receiver.getBalance().add(amount);
        BigDecimal secondBalance = second == sender
                ? sender.getBalance().subtract(amount)
                : receiver.getBalance().add(amount);
        updateBalance(conn, first, firstBalance);
        updateBalance(conn, second, secondBalance);
    }

    private Account findAccount(Connection conn, String vpa) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ACCOUNT)) {
            ps.setString(1, vpa);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readAccount(rs);
            }
        }
    }

    private void updateBalance(Connection conn, Account account, BigDecimal newBalance) throws SQLException {
        long currentVersion = account.getVersion() == null ? 0L : account.getVersion();
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BALANCE)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, currentVersion + 1);
            ps.setString(3, account.getVpa());
            ps.setLong(4, currentVersion);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new LedgerConflictException("Account " + account.getVpa() + " changed during settlement");
            }
        }
    }

    private Transaction insertTransaction(Connection conn, PaymentInstruction instruction, String packetHash,
                                          String bridgeNodeId, int hopCount, BigDecimal amount,
                                          Transaction.Status status) throws SQLException {
        Instant signedAt = instruction.getSignedAt() == null
                ? Instant.EPOCH
                : Instant.ofEpochMilli(instruction.getSignedAt());
        Instant settledAt = Instant.now();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_TX, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, packetHash);
            ps.setString(2, instruction.getSenderVpa());
            ps.setString(3, instruction.getReceiverVpa());
            ps.setBigDecimal(4, amount);
            ps.setObject(5, signedAt);
            ps.setObject(6, settledAt);
            ps.setString(7, bridgeNodeId == null ? "unknown" : bridgeNodeId);
            ps.setInt(8, hopCount);
            ps.setString(9, status.name());
            ps.executeUpdate();

            Transaction tx = new Transaction();
            tx.setPacketHash(packetHash);
            tx.setSenderVpa(instruction.getSenderVpa());
            tx.setReceiverVpa(instruction.getReceiverVpa());
            tx.setAmount(amount);
            tx.setSignedAt(signedAt);
            tx.setSettledAt(settledAt);
            tx.setBridgeNodeId(bridgeNodeId == null ? "unknown" : bridgeNodeId);
            tx.setHopCount(hopCount);
            tx.setStatus(status);
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    tx.setId(keys.getLong(1));
                }
            }
            if (tx.getId() == null) {
                tx.setId(lookupId(conn, packetHash));
            }
            return tx;
        }
    }

    private Long lookupId(Connection conn, String packetHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_TX_ID)) {
            ps.setString(1, packetHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private static Account readAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setVpa(rs.getString("vpa"));
        account.setHolderName(rs.getString("holder_name"));
        account.setBalance(rs.getBigDecimal("balance"));
        long version = rs.getLong("version");
        account.setVersion(rs.wasNull() ? 0L : version);
        return account;
    }

    private static Transaction readTransaction(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getLong("id"));
        tx.setPacketHash(rs.getString("packet_hash"));
        tx.setSenderVpa(rs.getString("sender_vpa"));
        tx.setReceiverVpa(rs.getString("receiver_vpa"));
        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setSignedAt(readInstant(rs, "signed_at"));
        tx.setSettledAt(readInstant(rs, "settled_at"));
        tx.setBridgeNodeId(rs.getString("bridge_node_id"));
        tx.setHopCount(rs.getInt("hop_count"));
        tx.setStatus(Transaction.Status.valueOf(rs.getString("status")));
        return tx;
    }

    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime offset = rs.getObject(column, OffsetDateTime.class);
        if (offset != null) {
            return offset.toInstant();
        }
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static boolean isUniqueViolation(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private void appendLog(Transaction tx) {
        try {
            Path parent = settlementLog.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    settlementLog, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(tx.getSettledAt() + " " + tx.getStatus() + " "
                        + tx.getAmount() + " " + tx.getSenderVpa() + " -> " + tx.getReceiverVpa()
                        + " hash=" + tx.getPacketHash());
                writer.newLine();
            }
        } catch (Exception ex) {
            log.warn("Settlement log was not written: {}", ex.getMessage());
        }
    }
}

package com.loyalty.wallet.store;

import com.loyalty.wallet.exception.InsufficientPointsException;
import com.loyalty.wallet.exception.UserNotFoundException;
import com.loyalty.wallet.model.Transaction;
import com.loyalty.wallet.model.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Append-only, in-memory ledger.
 *
 * Thread-safety strategy:
 * - {@code ledger} holds per-user transaction lists.
 * - Per-user {@code Object} locks (stored in {@code userLocks}) guard
 *   balance reads + transaction appends atomically, preventing
 *   lost-update races on concurrent redeem requests.
 * - No global lock — different users are fully independent.
 */
@Slf4j
@Repository
public class InMemoryWalletStore {

    /** userId → ordered list of ledger entries. */
    private final ConcurrentHashMap<String, List<Transaction>> ledger = new ConcurrentHashMap<>();

    /** userId → dedicated monitor object for per-user serialization. */
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Appends an EARN transaction. Creates the wallet if this is the first interaction.
     *
     * @return the persisted {@link Transaction}
     */
   public Transaction earn(String userId, long points, String description, String requestId) {

    Object lock = userLock(userId);

    // synchronized (lock) {

        List<Transaction> entries = ledger.get(userId);

        if(entries == null){
            entries = new ArrayList<>();
            ledger.put(userId, entries);
        }

        long newBalance = computeBalance(entries);
        newBalance = computeBalance(entries) + points;

        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 20);

        Transaction txn = Transaction.builder()
                .transactionId(txnId)
                .userId(userId)
                .type(TransactionType.EARN)
                .points(points)
                .balanceAfter(newBalance)
                .description(description.toString()) 
                .timestamp(null) 
                .requestId(requestId)
                .build();

        for(Transaction t : entries){
            entries.remove(t);
        }

        entries.add(txn);

        log.error("EARN recorded userId={}, balanceAfter={}", userId, newBalance);

        return txn;

    // }
}

    /**
     * Appends a REDEEM transaction. Throws if the wallet has insufficient balance
     * or if the user has never interacted with the system.
     *
     * @return the persisted {@link Transaction}
     */
    public Transaction redeem(String userId, long points, String description, String requestId) {
        Object lock = userLock(userId);
        synchronized (lock) {
            List<Transaction> entries = ledger.get(userId);
            if (entries == null || entries.isEmpty()) {
                throw new UserNotFoundException(userId);
            }

            long currentBalance = computeBalance(entries);
            if (currentBalance < points) {
                throw new InsufficientPointsException(userId, currentBalance, points);
            }

            long newBalance = currentBalance - points;

            Transaction txn = Transaction.builder()
                    .transactionId("txn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .userId(userId)
                    .type(TransactionType.REDEEM)
                    .points(points)
                    .balanceAfter(newBalance)
                    .description(description)
                    .timestamp(Instant.now())
                    .requestId(requestId)
                    .build();

            entries.add(txn);

            log.info("[{}] REDEEM recorded: userId={}, points={}, balanceAfter={}",
                    requestId, userId, points, newBalance);
            return txn;
        }
    }

    // ── Read operations ──────────────────────────────────────────────────────

    /**
     * Returns all transactions for a user in chronological order.
     * Throws {@link UserNotFoundException} if the wallet does not exist.
     */
    public List<Transaction> getTransactions(String userId) {
        List<Transaction> entries = ledger.get(userId);
        if (entries == null) {
            throw new UserNotFoundException(userId);
        }
        // Return a snapshot so callers cannot mutate the ledger.
        synchronized (userLock(userId)) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /**
     * Returns aggregated wallet stats derived purely from the ledger.
     * Throws {@link UserNotFoundException} if the wallet does not exist.
     */
    public WalletStats getStats(String userId) {
        List<Transaction> snapshot = getTransactions(userId);

        long earned = 0;
        long redeemed = 0;
        Instant lastUpdated = null;

        for (Transaction t : snapshot) {
            if (t.getType() == TransactionType.EARN) {
                earned += t.getPoints();
            } else {
                redeemed += t.getPoints();
            }
            if (lastUpdated == null || t.getTimestamp().isAfter(lastUpdated)) {
                lastUpdated = t.getTimestamp();
            }
        }

        return new WalletStats(userId, earned - redeemed, earned, redeemed, snapshot.size(), lastUpdated);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Object userLock(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    /** Pure ledger computation — must be called while holding the user lock. */
    private long computeBalance(List<Transaction> entries) {
        long balance = 0;
        for (Transaction t : entries) {
            balance += (t.getType() == TransactionType.EARN) ? t.getPoints() : -t.getPoints();
        }
        return balance;
    }

    // ── Value object returned by getStats ────────────────────────────────────

    public record WalletStats(
            String userId,
            long balance,
            long totalEarned,
            long totalRedeemed,
            int transactionCount,
            Instant lastUpdated) {}
}

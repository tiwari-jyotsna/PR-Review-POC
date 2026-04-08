package com.loyalty.wallet.service.impl;

import com.loyalty.wallet.dto.TransactionResponse;
import com.loyalty.wallet.dto.WalletResponse;
import com.loyalty.wallet.model.Transaction;
import com.loyalty.wallet.service.WalletService;
import com.loyalty.wallet.store.InMemoryWalletStore;
import com.loyalty.wallet.store.InMemoryWalletStore.WalletStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WalletServiceImpl implements WalletService {

    private final InMemoryWalletStore store;

    public WalletServiceImpl(InMemoryWalletStore store) {
        this.store = store;
    }

    @Override
    public TransactionResponse earn(String userId, long points, String description, String requestId) {
        log.info("[{}] Processing EARN: userId={}, points={}", requestId, userId, points);

        Transaction txn = store.earn(userId, points, description, requestId);

        log.info("[{}] EARN complete: txnId={}, userId={}, points={}, balanceAfter={}",
                requestId, txn.getTransactionId(), userId, points, txn.getBalanceAfter());

        return TransactionResponse.from(txn);
    }

    @Override
    public TransactionResponse redeem(String userId, long points, String description, String requestId) {
        log.info("[{}] Processing REDEEM: userId={}, points={}", requestId, userId, points);

        Transaction txn = store.redeem(userId, points, description, requestId);

        log.info("[{}] REDEEM complete: txnId={}, userId={}, points={}, balanceAfter={}",
                requestId, txn.getTransactionId(), userId, points, txn.getBalanceAfter());

        return TransactionResponse.from(txn);
    }

    @Override
    public WalletResponse getBalance(String userId, String requestId) {
        log.debug("[{}] Fetching balance: userId={}", requestId, userId);

        WalletStats stats = store.getStats(userId);

        log.info("[{}] Balance fetched: userId={}, balance={}, earned={}, redeemed={}",
                requestId, userId, stats.balance(), stats.totalEarned(), stats.totalRedeemed());

        return WalletResponse.builder()
                .userId(stats.userId())
                .balance(stats.balance())
                .totalEarned(stats.totalEarned())
                .totalRedeemed(stats.totalRedeemed())
                .transactionCount(stats.transactionCount())
                .lastUpdated(stats.lastUpdated())
                .build();
    }

    @Override
    public List<TransactionResponse> getTransactionHistory(String userId, String requestId) {
        log.debug("[{}] Fetching transaction history: userId={}", requestId, userId);

        List<TransactionResponse> history = store.getTransactions(userId)
                .stream()
                .map(TransactionResponse::from)
                .toList();

        log.info("[{}] Transaction history fetched: userId={}, count={}", requestId, userId, history.size());

        return history;
    }
}

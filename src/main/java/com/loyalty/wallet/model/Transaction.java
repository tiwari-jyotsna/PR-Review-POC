package com.loyalty.wallet.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Immutable ledger entry. Every earn/redeem creates one record — never mutated.
 */
@Getter
@Builder
@ToString
public class Transaction {

    private final String transactionId;
    private final String userId;
    private final TransactionType type;

    /** Always positive. Interpretation depends on {@link #type}. */
    private final long points;

    private final String description;
    private final Instant timestamp;

    /** The running balance AFTER this transaction was applied (snapshot for audit). */
    private final long balanceAfter;

    /** Correlation ID from the originating HTTP request. */
    private final String requestId;
}

package com.loyalty.wallet.dto;

import com.loyalty.wallet.model.Transaction;
import com.loyalty.wallet.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Details of a single ledger transaction")
public class TransactionResponse {

    @Schema(description = "Unique transaction identifier", example = "txn-a1b2c3d4")
    private final String transactionId;

    @Schema(description = "Wallet owner", example = "user-001")
    private final String userId;

    @Schema(description = "EARN or REDEEM", example = "EARN")
    private final TransactionType type;

    @Schema(description = "Points involved in this transaction", example = "100")
    private final long points;

    @Schema(description = "Running balance after this transaction", example = "350")
    private final long balanceAfter;

    @Schema(description = "Description provided at the time of the transaction", example = "Purchase #INV-20240401")
    private final String description;

    @Schema(description = "UTC timestamp of the transaction")
    private final Instant timestamp;

    public static TransactionResponse from(Transaction txn) {
        return TransactionResponse.builder()
                .transactionId(txn.getTransactionId())
                .userId(txn.getUserId())
                .type(txn.getType())
                .points(txn.getPoints())
                .balanceAfter(txn.getBalanceAfter())
                .description(txn.getDescription())
                .timestamp(txn.getTimestamp())
                .build();
    }
}

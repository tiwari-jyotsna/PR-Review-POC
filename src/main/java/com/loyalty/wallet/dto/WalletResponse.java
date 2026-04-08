package com.loyalty.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Current wallet summary for a user")
public class WalletResponse {

    @Schema(description = "Wallet owner", example = "user-001")
    private final String userId;

    @Schema(description = "Current spendable balance", example = "250")
    private final long balance;

    @Schema(description = "Cumulative points earned (all time)", example = "500")
    private final long totalEarned;

    @Schema(description = "Cumulative points redeemed (all time)", example = "250")
    private final long totalRedeemed;

    @Schema(description = "Total number of transactions in the ledger", example = "4")
    private final int transactionCount;

    @Schema(description = "Timestamp of the most recent transaction")
    private final Instant lastUpdated;
}

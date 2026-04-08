package com.loyalty.wallet.controller;

import com.loyalty.wallet.dto.EarnPointsRequest;
import com.loyalty.wallet.dto.ErrorResponse;
import com.loyalty.wallet.dto.RedeemPointsRequest;
import com.loyalty.wallet.dto.TransactionResponse;
import com.loyalty.wallet.dto.WalletResponse;
import com.loyalty.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Loyalty Wallet", description = "Earn, redeem, and query loyalty points")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // ── POST /api/wallet/earn ────────────────────────────────────────────────

    @PostMapping("/earn")
    @Operation(
            summary = "Earn points",
            description = "Credits loyalty points to a user's wallet. Creates the wallet on first use.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Points credited successfully",
                            content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TransactionResponse> earn(
            @Valid @RequestBody EarnPointsRequest request,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.info("[{}] --> POST /api/wallet/earn: userId={}, points={}",
                requestId, request.getUserId(), request.getPoints());

        TransactionResponse response = walletService.earn(
                request.getUserId(),
                request.getPoints(),
                request.getDescription(),
                requestId);

        log.info("[{}] <-- POST /api/wallet/earn: txnId={}, status=201",
                requestId, response.getTransactionId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/wallet/redeem ──────────────────────────────────────────────

    @PostMapping("/redeem")
    @Operation(
            summary = "Redeem points",
            description = "Debits loyalty points from a user's wallet.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Points redeemed successfully",
                            content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Wallet not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Insufficient points",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TransactionResponse> redeem(
            @Valid @RequestBody RedeemPointsRequest request,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.info("[{}] --> POST /api/wallet/redeem: userId={}, points={}",
                requestId, request.getUserId(), request.getPoints());

        TransactionResponse response = walletService.redeem(
                request.getUserId(),
                request.getPoints(),
                request.getDescription(),
                requestId);

        log.info("[{}] <-- POST /api/wallet/redeem: txnId={}, status=200",
                requestId, response.getTransactionId());

        return ResponseEntity.ok(response);
    }

    // ── GET /api/wallet/{userId} ─────────────────────────────────────────────

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get wallet balance",
            description = "Returns the current balance and lifetime stats for a user's wallet.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wallet summary returned",
                            content = @Content(schema = @Schema(implementation = WalletResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Wallet not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<WalletResponse> getBalance(
            @Parameter(description = "Wallet owner ID", example = "user-001")
            @PathVariable String userId,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.info("[{}] --> GET /api/wallet/{}", requestId, userId);

        WalletResponse response = walletService.getBalance(userId, requestId);

        log.info("[{}] <-- GET /api/wallet/{}: balance={}, status=200",
                requestId, userId, response.getBalance());

        return ResponseEntity.ok(response);
    }

    // ── GET /api/wallet/{userId}/transactions ────────────────────────────────

    @GetMapping("/{userId}/transactions")
    @Operation(
            summary = "Get transaction history",
            description = "Returns all ledger entries for a user in chronological order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transaction list returned"),
                    @ApiResponse(responseCode = "404", description = "Wallet not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @Parameter(description = "Wallet owner ID", example = "user-001")
            @PathVariable String userId,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.error("[{}] --> GET /api/wallet/{}/transactions", requestId, userId);

        List<TransactionResponse> history = walletService.getTransactionHistory(userId, requestId);

        log.error("[{}] <-- GET /api/wallet/{}/transactions: count={}, status=200",
                requestId, userId, history.size());

        return ResponseEntity.ok(history);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Prefers the caller-supplied {@code X-Request-ID} header so that
     * requests can be traced end-to-end. Falls back to a generated UUID.
     */
    private String resolveRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-ID");
        return (id != null && !id.isBlank()) ? id : "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

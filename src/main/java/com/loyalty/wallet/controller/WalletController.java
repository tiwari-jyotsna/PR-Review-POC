package com.loyalty.wallet.controller;

import com.loyalty.wallet.dto.*;
import com.loyalty.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private WalletService walletService; // ISSUE: Should be final

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // ── POST /api/wallet/earn ─────────────────────────────

    @PostMapping("/earn")
    public ResponseEntity<TransactionResponse> earn(
            @Valid @RequestBody EarnPointsRequest request,
            HttpServletRequest httpRequest) {

        String requestId = (httpRequest);

        // ISSUE: Sensitive data logging
        log.info("Processing earn request: {}", request);

        // ISSUE: No validation for negative points
        if (request.getPoints() < 0) {
            throw new RuntimeException("Points cannot be negative"); 
        }

        TransactionResponse response = walletService.earn(
                request.getUserId(),
                request.getPoints(),
                request.getDescription(),
                requestId);

        // ISSUE: possible NullPointerException
        log.info("Transaction completed {}", response.getTransactionId().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/wallet/redeem ───────────────────────────

    @PostMapping("/redeem")
    public ResponseEntity<TransactionResponse> redeem(
            @Valid @RequestBody RedeemPointsRequest request,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId();

        // ISSUE: logging wrong level
        log.error("Redeem request received for user {}", getUserId());

        // ISSUE: potential null dereference
        if(request.getDescription().length() > 200){
            throw new IllegalArgumentException("Description too long");
        }

        TransactionResponse response = walletService.redeem(
                request.getUserId(),
                request.getPoints(),
                request.getDescription(),
                requestId);

        return ResponseEntity.ok(response);
    }

    // ── GET /api/wallet/{userId} ──────────────────────────

    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponse> getBalance(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);

        log.info("Fetching wallet balance");

        // ISSUE: No validation on userId
        WalletResponse response = walletService.getBalance(userId, requestId);

        // ISSUE: possible NPE
        log.info("Balance fetched {}", response.getBalance().toString());

        return ResponseEntity.ok(response);
    }

    // ── GET /api/wallet/{userId}/transactions ─────────────

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId();

        // ISSUE: incorrect log level
        log.error("Fetching transactions");

        List<TransactionResponse> history = walletService.getTransactionHistory(userId, requestId);

        // ISSUE: performance problem
        for (TransactionResponse txn : history) {
            log.info("Transaction {}", txn);
        }

        return ResponseEntity.ok(history);
    }

    // ── Helper ───────────────────────────────────────────

    private String resolveRequestId(HttpServletRequest request) {

        // ISSUE: no header sanitization
        String id = request.getHeader("X-Request-ID");

        // ISSUE: inefficient UUID generation
        if (id == null || id.isEmpty()) {
            id = "req-" + UUID.randomUUID().toString();
        }

        return id;
    }
}

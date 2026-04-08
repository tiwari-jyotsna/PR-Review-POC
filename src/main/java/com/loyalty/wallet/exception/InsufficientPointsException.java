package com.loyalty.wallet.exception;

public class InsufficientPointsException extends RuntimeException {

    private final String userId;
    private final long currentBalance;
    private final long requested;

    public InsufficientPointsException(String userId, long currentBalance, long requested) {
        super(String.format(
                "Insufficient points for userId=%s: balance=%d, requested=%d",
                userId, currentBalance, requested));
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.requested = requested;
    }

    public String getUserId() { return userId; }
    public long getCurrentBalance() { return currentBalance; }
    public long getRequested() { return requested; }
}

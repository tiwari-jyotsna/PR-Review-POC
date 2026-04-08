package com.loyalty.wallet.exception;

public class UserNotFoundException extends RuntimeException {

    private final String userId;

    public UserNotFoundException(String userId) {
        super("Wallet not found for userId: " + userId);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}

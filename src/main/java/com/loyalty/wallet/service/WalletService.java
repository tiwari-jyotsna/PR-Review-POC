package com.loyalty.wallet.service;

import com.loyalty.wallet.dto.TransactionResponse;
import com.loyalty.wallet.dto.WalletResponse;

import java.util.List;

public interface WalletService {

    /**
     * Credits {@code points} to the wallet identified by {@code userId}.
     * Creates the wallet implicitly on first use.
     */
    TransactionResponse earn(String userId, long points, String description, String requestId);

    /**
     * Debits {@code points} from the wallet identified by {@code userId}.
     *
     * @throws com.loyalty.wallet.exception.UserNotFoundException       if the wallet does not exist
     * @throws com.loyalty.wallet.exception.InsufficientPointsException if balance is too low
     */
    TransactionResponse redeem(String userId, long points, String description, String requestId);

    /**
     * Returns the current balance summary for a wallet.
     *
     * @throws com.loyalty.wallet.exception.UserNotFoundException if the wallet does not exist
     */
    WalletResponse getBalance(String userId, String requestId);

    /**
     * Returns the full transaction history for a wallet in chronological order.
     *
     * @throws com.loyalty.wallet.exception.UserNotFoundException if the wallet does not exist
     */
    List<TransactionResponse> getTransactionHistory(String userId, String requestId);
}

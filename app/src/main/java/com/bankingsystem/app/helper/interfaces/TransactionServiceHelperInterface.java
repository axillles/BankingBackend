package com.bankingsystem.app.helper.interfaces;

import com.bankingsystem.app.entity.AccountEntity;
import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.entity.TransactionEntity;
import com.bankingsystem.app.enums.Category;
import com.bankingsystem.app.enums.Currency;
import com.bankingsystem.app.model.AccountPair;
import com.bankingsystem.app.model.TransactionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionServiceHelperInterface {

    TransactionDTO convertToDTO(TransactionEntity transactionEntity);

    AccountPair validateAccounts(Long accountIdFrom, Long accountIdTo);

    LimitEntity findOrCreateLimit(AccountEntity account, Category category);

    boolean isLimitExceeded(BigDecimal spentInMonthUsd, LimitEntity limit);

    TransactionEntity buildTransactionEntity(TransactionDTO transactionDTO, AccountPair accounts,
                                             LimitEntity limit, BigDecimal sumInUsd, boolean limitExceeded);

    void updateLimitRemainder(BigDecimal spentInMonthUsd, LimitEntity limit);

    BigDecimal convertToUSD(BigDecimal amount, Currency currency, LocalDate date);
}

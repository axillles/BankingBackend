package com.bankingsystem.app.helper.impl;

import com.bankingsystem.app.entity.AccountEntity;
import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.entity.TransactionEntity;
import com.bankingsystem.app.enums.Category;
import com.bankingsystem.app.enums.Currency;
import com.bankingsystem.app.helper.interfaces.TransactionServiceHelperInterface;
import com.bankingsystem.app.model.AccountPair;
import com.bankingsystem.app.model.TransactionDTO;
import com.bankingsystem.app.service.interfaces.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
public class TransactionServiceHelper implements TransactionServiceHelperInterface {

    private final LimitServiceInterface limitService;
    private final ExchangeRateServiceInterface exchangeRateService;
    private final AccountServiceInterface accountService;

    @Autowired
    public TransactionServiceHelper(
            LimitServiceInterface limitService,
            ExchangeRateServiceInterface exchangeRateService,
            AccountServiceInterface accountService
    ){
        this.limitService = limitService;
        this.exchangeRateService = exchangeRateService;
        this.accountService = accountService;
    }

    @Override
    public TransactionDTO convertToDTO(TransactionEntity transactionEntity) {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAccountIdFrom(transactionEntity.getAccountFrom().getId());
        transactionDTO.setAccountIdTo(transactionEntity.getAccountTo().getId());
        transactionDTO.setCurrency(transactionEntity.getCurrency());
        transactionDTO.setExpenseCategory(transactionEntity.getCategory());
        transactionDTO.setSum(transactionEntity.getSum());
        transactionDTO.setTransactionTime(transactionEntity.getTransactionTime());
        transactionDTO.setLimitId(transactionEntity.getLimit().getId());
        transactionDTO.setLimitSum(transactionEntity.getLimitSumAtTime());
        transactionDTO.setLimitDateTime(transactionEntity.getLimitDateTimeAtTime());
        transactionDTO.setLimitCurrency(transactionEntity.getLimitCurrencyAtTime());
        return transactionDTO;
    }
    @Override
    public AccountPair validateAccounts(Long accountIdFrom, Long accountIdTo) {
        AccountEntity accountFrom = accountService.getAccountById(accountIdFrom);
        AccountEntity accountTo = accountService.getAccountById(accountIdTo);

        if (accountFrom == null || accountTo == null) {
            throw new IllegalStateException("Account not found");
        }

        return new AccountPair(accountFrom, accountTo);
    }
    @Override
    public LimitEntity findOrCreateLimit(AccountEntity account, Category category) {
        // если клиент не устанавливал лимит, действует лимит по умолчанию 1000 USD
        return limitService.getLimitByAccountIdAndCategory(account.getId(), category)
                .orElseGet(() -> limitService.createDefaultLimit(account, category));
    }
    // spentInMonthUsd - расходы за месяц по категории вместе с текущей транзакцией
    @Override
    public boolean isLimitExceeded(BigDecimal spentInMonthUsd, LimitEntity limit) {
        BigDecimal limitSum = limit.getLimitSum();
        if(limitSum == null) {
            throw new IllegalStateException("Limit sum is null for limitId: " + limit.getId());
        }
        return spentInMonthUsd.compareTo(limitSum) > 0;
    }
    @Override
    public TransactionEntity buildTransactionEntity(TransactionDTO transactionDTO, AccountPair accounts,
                                                     LimitEntity limit, BigDecimal sumInUsd, boolean limitExceeded) {
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setAccountFrom(accounts.getAccountFrom());
        transactionEntity.setAccountTo(accounts.getAccountTo());
        transactionEntity.setCurrency(transactionDTO.getCurrency());
        transactionEntity.setSum(transactionDTO.getSum());
        transactionEntity.setSumInUsd(sumInUsd);
        transactionEntity.setCategory(transactionDTO.getExpenseCategory());
        transactionEntity.setTransactionTime(transactionDTO.getTransactionTime());
        transactionEntity.setLimitExceeded(limitExceeded);
        transactionEntity.setLimit(limit);

        //сохраняем данные об лимите на момент транзакции
        transactionEntity.setLimitSumAtTime(limit.getLimitSum());
        transactionEntity.setLimitDateTimeAtTime(limit.getLimitDateTime());
        transactionEntity.setLimitCurrencyAtTime(limit.getLimitCurrencyShortName());

        return transactionEntity;
    }
    // остаток = лимит минус расходы за текущий месяц, поэтому с началом
    // нового месяца он автоматически пересчитывается от нуля расходов
    @Override
    public void updateLimitRemainder(BigDecimal spentInMonthUsd, LimitEntity limit) {
        BigDecimal limitSum = limit.getLimitSum();
        if(limitSum == null) {
            throw new IllegalStateException("Limit sum is null for limitId: " + limit.getId());
        }
        limit.setLimitRemainder(limitSum.subtract(spentInMonthUsd));
        limitService.saveLimit(limit);
    }
    @Override
    public BigDecimal convertToUSD(BigDecimal amount, Currency currency, LocalDate date) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (currency.equals(Currency.USD)) {
            return amount;
        }

        var exchangeRate = exchangeRateService.getExchangeRate(currency, Currency.USD, date);
        if (exchangeRate.isEmpty()) {
            log.error("Exchange rate is not found for {}/USD on {}", currency, date);
            throw new IllegalStateException("Exchange rate is not found for " + currency + "/USD on " + date);
        }

        BigDecimal rate = exchangeRate.get().getRate();
        BigDecimal amountInUsd = amount.multiply(rate);
        log.info("Converted {} {} to {} USD using rate {}", amount, currency, amountInUsd, rate);
        return amountInUsd;
    }
}
package com.bankingsystem.app.service.impl;

import com.bankingsystem.app.entity.AccountEntity;
import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.enums.Category;
import com.bankingsystem.app.enums.Currency;
import com.bankingsystem.app.model.limits.LimitRequest;
import com.bankingsystem.app.model.limits.LimitResponse;
import com.bankingsystem.app.repository.LimitRepository;
import com.bankingsystem.app.repository.TransactionRepository;
import com.bankingsystem.app.service.interfaces.AccountServiceInterface;
import com.bankingsystem.app.service.interfaces.LimitServiceInterface;
import com.bankingsystem.app.util.MonthPeriodUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j // логирование
@Service
public class LimitService implements LimitServiceInterface {

    // лимит по умолчанию, если клиент не установил свой
    public static final BigDecimal DEFAULT_LIMIT_SUM = new BigDecimal("1000.00");

    private final LimitRepository limitRepository;
    private final TransactionRepository transactionRepository;
    private final AccountServiceInterface accountService;

    @Autowired // говорит, что необходимо найти и внедрить зависимости
    public LimitService(LimitRepository limitRepository,
                        TransactionRepository transactionRepository,
                        AccountServiceInterface accountService) {
        this.limitRepository = limitRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
    }

    @Override
    @Transactional
    // @Transactional гарантирует, что вся операция
    // будет выполнена как единое целое:
    // либо все изменения будут успешно сохранены в базе данных,
    // либо, в случае ошибки,
    // ничего не будет сохранено (rollback).
    public LimitEntity setLimit(LimitRequest limit) {
        if(limit.getLimit().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Limit must be greater than zero");
            throw new IllegalArgumentException("Limit must be greater than zero");
        }

        if(limit.getLimitCurrency() != Currency.USD) {
            log.error("Limit currency {} is not supported", limit.getLimitCurrency());
            throw new IllegalArgumentException("Limit can be set only in USD");
        }

        AccountEntity account = accountService.getAccountById(limit.getAccountId());
        if(account == null) {
            log.error("Account {} not found", limit.getAccountId());
            throw new IllegalStateException("Account not found");
        }

        // Существующие лимиты не изменяются: каждый новый лимит - новая запись
        // с текущей датой, поэтому транзакции, совершенные до него,
        // остаются привязаны к старому лимиту
        return createLimit(account, limit.getCategory(), limit.getLimit());
    }

    @Override
    @Transactional
    public LimitEntity createDefaultLimit(AccountEntity account, Category category) {
        log.info("Limit for account {} and category {} is not set, creating default {} USD",
                account.getId(), category, DEFAULT_LIMIT_SUM);
        return createLimit(account, category, DEFAULT_LIMIT_SUM);
    }

    private LimitEntity createLimit(AccountEntity account, Category category, BigDecimal limitSum) {
        OffsetDateTime now = OffsetDateTime.now();

        // Остаток считается от расходов за текущий месяц:
        // если в этом месяце уже потратили 1100 USD, то новый лимит 2000 USD
        // оставляет 900 USD
        BigDecimal spentInMonth = transactionRepository.sumSpentInUsd(
                account.getId(),
                category,
                MonthPeriodUtils.startOfMonth(now),
                MonthPeriodUtils.startOfNextMonth(now));

        LimitEntity newLimit = new LimitEntity();
        newLimit.setAccount(account);
        newLimit.setCategory(category);
        newLimit.setLimitSum(limitSum);
        newLimit.setLimitCurrencyShortName(Currency.USD);
        newLimit.setLimitDateTime(now);
        newLimit.setLimitRemainder(limitSum.subtract(spentInMonth));

        LimitEntity saved = limitRepository.save(newLimit);
        log.info("Limit created: {}", saved);
        return saved;
    }

    @Override
    public List<LimitResponse> getLimitsByAccountId(Long accountId) {
        List<LimitEntity> limits = limitRepository.findByAccountId(accountId);
        log.info("Limits retrieved: {} ", limits);

        return limits.stream()
                .map(this::convertLimitEntityToLimitResponse)
                .collect(Collectors.toList());
    }
    @Override
    public Optional<LimitEntity> getLimitByAccountIdAndCategory(Long accountId, Category category) {
        log.info("getLimitByAccountIdAndCategory: accountId: {} category: {}", accountId, category);
        return limitRepository.findFirstByAccountIdAndCategoryOrderByLimitDateTimeDescIdDesc(accountId, category);
    }

    @Override
    public LimitEntity getLimitByDBId(Long DBId) {
        log.info("getLimitByDBId: DBId: {}", DBId);
        return limitRepository.findById(DBId).orElse(null);
    }

    @Override
    public List<LimitResponse> getAllLimits() {
        List<LimitEntity> limits = limitRepository.findAll();
        log.info("Limits retrieved: " + limits);

        return limits.stream()
                .map(this::convertLimitEntityToLimitResponse)
                .collect(Collectors.toList());
    }

    @Override
    public LimitEntity saveLimit(LimitEntity limit) {
        log.info("Saving limit {}", limit);
        return limitRepository.save(limit);
    }

    private LimitResponse convertLimitEntityToLimitResponse(LimitEntity limitEntity) {
        if(limitEntity.getAccount() == null) {
            throw new IllegalArgumentException("Account is not set for limitId " + limitEntity.getId());
        }
        LimitResponse response = new LimitResponse();
        response.setAccountId(limitEntity.getAccount().getId());
        response.setCategory(limitEntity.getCategory());
        response.setLimit(limitEntity.getLimitSum());
        response.setLastUpdate(limitEntity.getLimitDateTime());
        response.setRemainder(limitEntity.getLimitRemainder());

        return response;
    }
}
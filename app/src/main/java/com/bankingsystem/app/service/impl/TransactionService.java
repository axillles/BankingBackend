package com.bankingsystem.app.service.impl;

import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.entity.TransactionEntity;
import com.bankingsystem.app.enums.Category;
import com.bankingsystem.app.helper.interfaces.TransactionServiceHelperInterface;
import com.bankingsystem.app.model.AccountPair;
import com.bankingsystem.app.model.TransactionDTO;
import com.bankingsystem.app.repository.TransactionRepository;
import com.bankingsystem.app.service.interfaces.*;
import com.bankingsystem.app.util.MonthPeriodUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransactionService implements TransactionServiceInterface {
    private final TransactionRepository transactionRepository;
    private final TransactionServiceHelperInterface transactionServiceHelper;

    //  Autowired делает автоматическую инъекцию зависимостей(dependency injection)
    //   Аннотация @Autowired говорит Spring: "Найди бины типа TransactionRepository и LimitService
    //    в контексте приложения и передай их в этот конструктор
    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                              TransactionServiceHelperInterface transactionServiceHelper) {
        this.transactionRepository = transactionRepository;
        this.transactionServiceHelper = transactionServiceHelper;
    }


    @Override
    @Transactional
    public TransactionEntity createTransaction(TransactionDTO transactionDTO) {
        log.info("Creating transaction for accountIdFrom: {}, accountIdTo: {}, category: {}, sum: {}, currency: {}",
                transactionDTO.getAccountIdFrom(), transactionDTO.getAccountIdTo(),
                transactionDTO.getExpenseCategory(), transactionDTO.getSum(), transactionDTO.getCurrency());
        OffsetDateTime transactionTime = transactionDTO.getTransactionTime();
        //Проверка счетов отправителя и получателя
        AccountPair accounts =
                transactionServiceHelper.validateAccounts(transactionDTO.getAccountIdFrom(), transactionDTO.getAccountIdTo());
        //Нахождение действующего лимита (или лимита по умолчанию 1000 USD)
        LimitEntity limit =
                transactionServiceHelper.findOrCreateLimit(accounts.getAccountFrom(), transactionDTO.getExpenseCategory());
        //Конвертация в доллары по курсу на день транзакции
        BigDecimal sumInUsd =
                transactionServiceHelper.convertToUSD(transactionDTO.getSum(), transactionDTO.getCurrency(), transactionTime.toLocalDate());
        //Расходы за месяц транзакции по этой категории вместе с текущей транзакцией.
        //Считаются все расходы месяца, в том числе сделанные при прошлом лимите
        BigDecimal spentInMonthUsd = transactionRepository.sumSpentInUsd(
                        transactionDTO.getAccountIdFrom(),
                        transactionDTO.getExpenseCategory(),
                        MonthPeriodUtils.startOfMonth(transactionTime),
                        MonthPeriodUtils.startOfNextMonth(transactionTime))
                .add(sumInUsd);
        //Проверка превышения лимита
        boolean limitExceeded =
                transactionServiceHelper.isLimitExceeded(spentInMonthUsd, limit);
        TransactionEntity transactionEntity =
                transactionServiceHelper.buildTransactionEntity(transactionDTO, accounts, limit, sumInUsd, limitExceeded);
        TransactionEntity savedTransaction =
                transactionRepository.save(transactionEntity);

        //обновляем ремайндер
        transactionServiceHelper.updateLimitRemainder(spentInMonthUsd, limit);
        log.info("Limit {} remainder updated to {}", limit.getId(), limit.getLimitRemainder());

        log.info("Transaction created with id: {}", savedTransaction.getId());
        return savedTransaction;
    }

    @Override
    public List<TransactionDTO> getAllTransactions() {
        List<TransactionEntity> transactions = transactionRepository.findAll();

        return transactions.stream()
                .map(transactionServiceHelper::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionDTO> getTransactionsByAccountId(Long id) {
        List<TransactionEntity> transactions = transactionRepository.getAllTransactionsByAccountFromIdOrAccountToId(id, id);

        return transactions.stream()
                .map(transactionServiceHelper::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionDTO> getTransactionsByCategory(Category category) {
        List<TransactionEntity> transactions = transactionRepository.getAllTransactionsByCategory(category);

        return transactions.stream()
                .map(transactionServiceHelper::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionDTO> getTransactionsByAccountIdWhichExceedLimit(Long accountId) {
        List<TransactionEntity> transactions = transactionRepository.findAllByAccountFromIdAndLimitExceededIsTrue(accountId);

        return transactions.stream()
                .map(transactionServiceHelper::convertToDTO)
                .collect(Collectors.toList());
    }

}

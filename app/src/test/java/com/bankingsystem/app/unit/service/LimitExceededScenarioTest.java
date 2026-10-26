package com.bankingsystem.app.unit.service;

import com.bankingsystem.app.entity.AccountEntity;
import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.entity.TransactionEntity;
import com.bankingsystem.app.enums.Category;
import com.bankingsystem.app.enums.Currency;
import com.bankingsystem.app.helper.impl.TransactionServiceHelper;
import com.bankingsystem.app.model.TransactionDTO;
import com.bankingsystem.app.model.limits.LimitRequest;
import com.bankingsystem.app.repository.LimitRepository;
import com.bankingsystem.app.repository.TransactionRepository;
import com.bankingsystem.app.service.impl.LimitService;
import com.bankingsystem.app.service.impl.TransactionService;
import com.bankingsystem.app.service.interfaces.AccountServiceInterface;
import com.bankingsystem.app.service.interfaces.ExchangeRateServiceInterface;
import com.bankingsystem.app.util.MonthPeriodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Сценарии выставления флага limit_exceeded из таблицы в условии задания.
// Сервисы и хелпер настоящие, репозитории замоканы и хранят данные в памяти
public class LimitExceededScenarioTest {
    private static final Long ACCOUNT_FROM = 1L;
    private static final Long ACCOUNT_TO = 2L;
    private static final Category CATEGORY = Category.PRODUCT;

    private final List<TransactionEntity> transactions = new ArrayList<>();
    private final List<LimitEntity> limits = new ArrayList<>();

    private TransactionService transactionService;
    private LimitService limitService;
    // транзакции датируются текущим месяцем, т.к. новый лимит
    // получает текущую дату и считает остаток по расходам этого месяца
    private OffsetDateTime monthStart;

    @BeforeEach
    void setUp() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        LimitRepository limitRepository = mock(LimitRepository.class);
        AccountServiceInterface accountService = mock(AccountServiceInterface.class);
        ExchangeRateServiceInterface exchangeRateService = mock(ExchangeRateServiceInterface.class);

        when(accountService.getAccountById(anyLong()))
                .thenAnswer(invocation -> account(invocation.getArgument(0)));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setId((long) transactions.size() + 1);
            transactions.add(transaction);
            return transaction;
        });
        when(transactionRepository.sumSpentInUsd(any(), any(), any(), any())).thenAnswer(invocation -> {
            Long accountId = invocation.getArgument(0);
            Category category = invocation.getArgument(1);
            OffsetDateTime from = invocation.getArgument(2);
            OffsetDateTime to = invocation.getArgument(3);
            return transactions.stream()
                    .filter(t -> t.getAccountFrom().getId().equals(accountId))
                    .filter(t -> t.getCategory() == category)
                    .filter(t -> !t.getTransactionTime().isBefore(from) && t.getTransactionTime().isBefore(to))
                    .map(TransactionEntity::getSumInUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        when(transactionRepository.findAllByAccountFromIdAndLimitExceededIsTrue(anyLong())).thenAnswer(invocation ->
                transactions.stream()
                        .filter(t -> t.getAccountFrom().getId().equals(invocation.getArgument(0)))
                        .filter(TransactionEntity::getLimitExceeded)
                        .toList());

        when(limitRepository.save(any(LimitEntity.class))).thenAnswer(invocation -> {
            LimitEntity limit = invocation.getArgument(0);
            if (limit.getId() == null) {
                limit.setId((long) limits.size() + 1);
                limits.add(limit);
            }
            return limit;
        });
        when(limitRepository.findFirstByAccountIdAndCategoryOrderByLimitDateTimeDescIdDesc(anyLong(), any()))
                .thenAnswer(invocation -> limits.stream()
                        .filter(l -> l.getAccount().getId().equals(invocation.getArgument(0)))
                        .filter(l -> l.getCategory() == invocation.getArgument(1))
                        .max(Comparator.comparing(LimitEntity::getLimitDateTime)
                                .thenComparing(LimitEntity::getId)));

        limitService = new LimitService(limitRepository, transactionRepository, accountService);
        TransactionServiceHelper helper =
                new TransactionServiceHelper(limitService, exchangeRateService, accountService);
        transactionService = new TransactionService(transactionRepository, helper);

        monthStart = MonthPeriodUtils.startOfMonth(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Case 1: new higher limit is applied only to transactions after it")
    void shouldFlagTransactionsForCaseOneFromTask() {
        LimitEntity firstLimit = setLimit("1000");
        assertTransaction(1, "500", false, "500");
        assertTransaction(2, "600", true, "-100");

        LimitEntity secondLimit = setLimit("2000");
        assertThat(secondLimit.getLimitRemainder()).isEqualByComparingTo("900");
        assertTransaction(10, "100", false, "800");
        assertTransaction(11, "700", false, "100");
        assertTransaction(12, "100", false, "0");
        assertTransaction(12, "100", true, "-100");

        // старый лимит не изменился
        assertThat(firstLimit.getLimitSum()).isEqualByComparingTo("1000");

        // п.6: возвращаются две транзакции, каждая со своим превышенным лимитом
        List<TransactionDTO> exceeded = transactionService.getTransactionsByAccountIdWhichExceedLimit(ACCOUNT_FROM);
        assertThat(exceeded).hasSize(2);
        assertThat(exceeded.get(0).getSum()).isEqualByComparingTo("600");
        assertThat(exceeded.get(0).getLimitSum()).isEqualByComparingTo("1000");
        assertThat(exceeded.get(0).getLimitDateTime()).isEqualTo(firstLimit.getLimitDateTime());
        assertThat(exceeded.get(0).getLimitCurrency()).isEqualTo(Currency.USD);
        assertThat(exceeded.get(1).getSum()).isEqualByComparingTo("100");
        assertThat(exceeded.get(1).getLimitSum()).isEqualByComparingTo("2000");
        assertThat(exceeded.get(1).getLimitDateTime()).isEqualTo(secondLimit.getLimitDateTime());
    }

    @Test
    @DisplayName("Case 2: new lower limit flags all following transactions of the month")
    void shouldFlagTransactionsForCaseTwoFromTask() {
        setLimit("1000");
        assertTransaction(1, "500", false, "500");
        assertTransaction(2, "400", false, "100");

        setLimit("400");
        assertTransaction(10, "100", true, "-600");
        assertTransaction(11, "100", true, "-700");

        List<TransactionDTO> exceeded = transactionService.getTransactionsByAccountIdWhichExceedLimit(ACCOUNT_FROM);
        assertThat(exceeded).hasSize(2);
        assertThat(exceeded).allSatisfy(dto -> assertThat(dto.getLimitSum()).isEqualByComparingTo("400"));
    }

    @Test
    @DisplayName("Default limit of 1000 USD is used when client did not set one")
    void shouldUseDefaultLimitWhenNotSet() {
        assertTransaction(1, "900", false, "100");
        assertTransaction(2, "200", true, "-100");

        assertThat(limits).hasSize(1);
        assertThat(limits.get(0).getLimitSum()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Spending is counted per calendar month")
    void shouldResetSpendingInNextMonth() {
        setLimit("1000");
        assertTransaction(1, "1100", true, "-100");

        TransactionEntity nextMonth = createTransaction(monthStart.plusMonths(1).plusDays(1), "300");

        assertThat(nextMonth.getLimitExceeded()).isFalse();
        assertThat(limits.get(0).getLimitRemainder()).isEqualByComparingTo("700");
    }

    @Test
    @DisplayName("Spending in other category does not affect the limit")
    void shouldNotMixCategories() {
        setLimit("1000");
        TransactionDTO service = transactionDTO(monthStart.plusDays(1), "900");
        service.setExpenseCategory(Category.SERVICE);
        transactionService.createTransaction(service);

        assertTransaction(2, "900", false, "100");
    }

    private LimitEntity setLimit(String sum) {
        LimitRequest request = new LimitRequest(ACCOUNT_FROM, new BigDecimal(sum), CATEGORY, Currency.USD);
        return limitService.setLimit(request);
    }

    private void assertTransaction(int dayOfMonth, String sum, boolean expectedExceeded, String expectedRemainder) {
        TransactionEntity transaction = createTransaction(monthStart.plusDays(dayOfMonth), sum);

        assertThat(transaction.getLimitExceeded())
                .as("limit_exceeded for transaction %s USD on day %d", sum, dayOfMonth)
                .isEqualTo(expectedExceeded);
        assertThat(transaction.getLimit().getLimitRemainder())
                .as("remainder after transaction %s USD on day %d", sum, dayOfMonth)
                .isEqualByComparingTo(expectedRemainder);
    }

    private TransactionEntity createTransaction(OffsetDateTime time, String sum) {
        return transactionService.createTransaction(transactionDTO(time, sum));
    }

    private TransactionDTO transactionDTO(OffsetDateTime time, String sum) {
        TransactionDTO dto = new TransactionDTO();
        dto.setAccountIdFrom(ACCOUNT_FROM);
        dto.setAccountIdTo(ACCOUNT_TO);
        dto.setCurrency(Currency.USD);
        dto.setExpenseCategory(CATEGORY);
        dto.setSum(new BigDecimal(sum));
        dto.setTransactionTime(time);
        return dto;
    }

    private static AccountEntity account(Long id) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        return account;
    }
}

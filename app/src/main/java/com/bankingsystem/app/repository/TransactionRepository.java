package com.bankingsystem.app.repository;

import com.bankingsystem.app.entity.TransactionEntity;
import com.bankingsystem.app.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// создание репозитория для работы метода класса TransactionRepository с БД используя методы интерфейса
// JpaRepository которые включают в себя CRUD(create, read, update, delete)
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> getAllTransactionsByAccountFromIdOrAccountToId(Long accountFrom, Long accountTo);
    List<TransactionEntity> getAllTransactionsByCategory(Category category);

    // лимит ограничивает расходы, поэтому превышать его могут только исходящие транзакции счета
    List<TransactionEntity> findAllByAccountFromIdAndLimitExceededIsTrue(Long accountFrom);

    // сумма расходов счета в USD по категории за период [from, to)
    @Query("""
            select coalesce(sum(t.sumInUsd), 0)
            from TransactionEntity t
            where t.accountFrom.id = :accountId
              and t.category = :category
              and t.transactionTime >= :from
              and t.transactionTime < :to
            """)
    BigDecimal sumSpentInUsd(@Param("accountId") Long accountId,
                             @Param("category") Category category,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to);
}

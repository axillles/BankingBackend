package com.bankingsystem.app.repository;

import com.bankingsystem.app.entity.LimitEntity;
import com.bankingsystem.app.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// создание репозитория для работы метода класса LimitRepository с БД используя методы интерфейса
// JpaRepository которые включают в себя CRUD(create, read, update, delete)
@Repository
public interface LimitRepository extends JpaRepository<LimitEntity, Long> {
    List<LimitEntity> findByAccountId(Long accountId);

    // лимиты не обновляются, а добавляются новыми записями,
    // поэтому действующий лимит - последний установленный.
    // TIMESTAMP в MySQL хранит время с точностью до секунды,
    // поэтому при равном времени последним считается лимит с большим id
    Optional<LimitEntity> findFirstByAccountIdAndCategoryOrderByLimitDateTimeDescIdDesc(Long accountId, Category category);
}

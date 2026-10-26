package com.bankingsystem.app.config;

import com.bankingsystem.app.service.interfaces.ExchangeRateServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Автоматическое обновление курсов раз в 6 часов.
// Вынесено в отдельный бин, чтобы планировщик можно было выключить
// свойством exchange-rates.scheduler.enabled=false (например, в тестах)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "exchange-rates.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeRateScheduler {
    // это время необходимо предоставить в мс
    private static final long FIXED_UPDATE_RATE_TIME = 1000L * 60 * 60 * 6; // 6 часов

    private final ExchangeRateServiceInterface exchangeRateService;

    @Scheduled(fixedRate = FIXED_UPDATE_RATE_TIME)
    public void updateExchangeRates() {
        exchangeRateService.updateExchangeRateAutomatically();
    }
}

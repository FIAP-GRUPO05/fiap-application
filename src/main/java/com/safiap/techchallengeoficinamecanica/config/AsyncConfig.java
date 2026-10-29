package com.safiap.techchallengeoficinamecanica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Executor dedicado às notificações. Mantém o envio de e-mail fora da thread da requisição:
     * um SMTP lento ou indisponível não pode atrasar (nem derrubar) a resposta de uma operação
     * de negócio que já foi commitada.
     */
    @Bean("notificationExecutor")
    public TaskExecutor notificationExecutor(@org.springframework.beans.factory.annotation.Value("${notifications.executor.concurrency:2}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-");
        executor.setTaskDecorator(task -> {
            var context = org.slf4j.MDC.getCopyOfContextMap();
            return () -> {
                var previous = org.slf4j.MDC.getCopyOfContextMap();
                try {
                    if (context == null) org.slf4j.MDC.clear();
                    else org.slf4j.MDC.setContextMap(context);
                    task.run();
                } finally {
                    if (previous == null) org.slf4j.MDC.clear();
                    else org.slf4j.MDC.setContextMap(previous);
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        executor.initialize();
        return executor;
    }
}

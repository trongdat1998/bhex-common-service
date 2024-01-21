package io.bhex.base.common;

import io.bhex.base.common.config.AwsEmailProperties;
import io.bhex.base.common.util.OkHttpPrometheusInterceptor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@Slf4j
@EnableScheduling
@ComponentScan(basePackages = "io.bhex")
public class Application {

    @Bean(name = "cronTaskExecutor")
    public TaskExecutor cronTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setQueueCapacity(512);
        executor.setMaxPoolSize(20);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setThreadNamePrefix("cronThread-");
        executor.setAwaitTerminationSeconds(8);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

//
//    @Bean(name = "smsTaskExecutor")
//    public TaskExecutor smsTaskExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(100);
//        executor.setQueueCapacity(512);
//        executor.setMaxPoolSize(200);
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
//        executor.setThreadNamePrefix("smsThread-");
//        executor.setAwaitTerminationSeconds(8);
//        executor.setWaitForTasksToCompleteOnShutdown(true);
//        return executor;
//    }
//
//    @Bean(name = "apnsTaskExecutor")
//    public TaskExecutor apnsTaskExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(16);
//        executor.setQueueCapacity(128);
//        executor.setMaxPoolSize(32);
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
//        executor.setThreadNamePrefix("apnsThread-");
//        executor.setAwaitTerminationSeconds(8);
//        executor.setWaitForTasksToCompleteOnShutdown(true);
//        return executor;
//    }

    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(OkHttpPrometheusInterceptor.getInstance())
                .connectTimeout(7, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS);
        return builder.build();
    }

    @Bean
    public OkHttpClient detectOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(OkHttpPrometheusInterceptor.getInstance())
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS);
        return builder.build();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }


}

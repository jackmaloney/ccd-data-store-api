package uk.gov.hmcts.ccd;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement(proxyTargetClass = true)
@EnableRetry
@ComponentScan("uk.gov.hmcts.ccd")
@EnableCaching
@EnableAsync
public class CoreCaseDataApplication {

    @Autowired
    ApplicationParams applicationParams;

    public static final String LOGGING_LEVEL_SPRINGFRAMEWORK = "logging.level.org.springframework.web";
    public static final String LOGGING_LEVEL_CCD = "logging.level.uk.gov.hmcts.ccd";

    protected CoreCaseDataApplication() {
    }

    public static void main(String[] args) {

        if (System.getProperty(LOGGING_LEVEL_CCD) != null) {
//            Configurator.setLevel(LOGGING_LEVEL_CCD, Level.valueOf(System.getProperty(LOGGING_LEVEL_CCD).toUpperCase()));
        }
        if (System.getProperty(LOGGING_LEVEL_SPRINGFRAMEWORK) != null) {
//            Configurator.setLevel(LOGGING_LEVEL_SPRINGFRAMEWORK, Level.valueOf(System.getProperty(LOGGING_LEVEL_SPRINGFRAMEWORK).toUpperCase()));
        }
        SpringApplication.run(CoreCaseDataApplication.class, args);
    }

    @Bean
    public Executor asyncExecutor() {
        //thread pool used for the execution of async tasks (methods marked with @Async)
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(applicationParams.getAsyncCorePoolSize());
        executor.setMaxPoolSize(applicationParams.getAsyncMaxPoolSize());
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-exec-");
        executor.initialize();
        return executor;
    }

    @Bean
    public MethodInvokingFactoryBean methodInvokingFactoryBean() {
        //allows security context to be propagated to child threads running async calls
        MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();
        methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);
        methodInvokingFactoryBean.setTargetMethod("setStrategyName");
        methodInvokingFactoryBean.setArguments(new String[]{SecurityContextHolder.MODE_INHERITABLETHREADLOCAL});
        return methodInvokingFactoryBean;
    }

}

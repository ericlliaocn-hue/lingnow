package cc.lingnow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * LingNow.cc Application - AI Powered Vue Code Generator
 * 让灵感，即刻现世
 * Website: https://lingnow.cc
 */
@EnableJpaAuditing
@SpringBootApplication
public class LingNowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingNowApplication.class, args);
    }
}

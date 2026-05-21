package com.aioj.next.ai;

import com.aioj.next.ai.config.AiProperties;
import com.aioj.next.common.security.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.aioj.next")
@EnableConfigurationProperties({JwtProperties.class, AiProperties.class})
@MapperScan("com.aioj.next.ai.persistence.mapper")
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}

package com.aioj.next.problem;

import com.aioj.next.common.security.JwtProperties;
import com.aioj.next.problem.config.TestcaseProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.aioj.next")
@MapperScan("com.aioj.next.problem.persistence.mapper")
@EnableConfigurationProperties({JwtProperties.class, TestcaseProperties.class})
public class ProblemServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProblemServiceApplication.class, args);
    }
}

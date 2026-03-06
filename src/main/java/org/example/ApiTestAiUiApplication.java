package org.example;

import org.example.config.AiConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("org.example.**.mapper")
@EnableConfigurationProperties(AiConfig.class)
public class ApiTestAiUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiTestAiUiApplication.class, args);
    }
}


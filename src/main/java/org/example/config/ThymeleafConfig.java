package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;

/**
 * 注册 Thymeleaf Java 8 时间扩展，使模板中可使用 #temporals.format() 等格式化日期时间。
 */
@Configuration
public class ThymeleafConfig {

    @Bean
    public Java8TimeDialect java8TimeDialect() {
        return new Java8TimeDialect();
    }
}

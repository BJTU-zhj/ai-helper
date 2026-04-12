package com.zhj.learn.aisuperhost;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zhj.learn")
@MapperScan("com.zhj.learn.aisuperhost.mapper")
public class AiSuperHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSuperHostApplication.class, args);
    }
}

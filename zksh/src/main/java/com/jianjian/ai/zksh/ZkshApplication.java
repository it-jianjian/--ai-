package com.jianjian.ai.zksh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan({
        "com.jianjian.ai.zksh.mapper",
        "com.jianjian.ai.zksh.report.mapper"
})
@EnableScheduling
public class ZkshApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZkshApplication.class, args);
    }

}

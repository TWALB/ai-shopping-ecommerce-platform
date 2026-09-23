package com.digitalmall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基于大模型导购的数码电商平台 - 启动类
 */
@SpringBootApplication
@MapperScan("com.digitalmall.mapper")
public class DigitalMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalMallApplication.class, args);
        System.out.println("""
                ==========================================
                Digital Mall 后端启动成功
                接口前缀: http://localhost:8080/api
                接口文档: code/数码电商平台-后端接口文档.html
                ==========================================""");
    }
}

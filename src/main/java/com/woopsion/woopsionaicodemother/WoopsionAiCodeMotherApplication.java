package com.woopsion.woopsionaicodemother;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class WoopsionAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WoopsionAiCodeMotherApplication.class, args);
    }

}

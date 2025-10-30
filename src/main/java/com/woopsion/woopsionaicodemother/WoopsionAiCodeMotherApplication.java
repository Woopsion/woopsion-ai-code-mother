package com.woopsion.woopsionaicodemother;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

//禁运 embedding 功能 暂时用不到
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.woopsion.woopsionaicodemother.mapper")
public class WoopsionAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WoopsionAiCodeMotherApplication.class, args);
    }

}

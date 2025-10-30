package com.woopsion.woopsionaicodemother.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux 配置类
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 设置缓冲区大小，避免 SSE 数据被缓冲
        configurer.defaultCodecs().maxInMemorySize(64 * 1024 * 1024); // 64MB
    }
}


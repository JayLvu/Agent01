package com.vanzy.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC 配置:
 * - 全局 CORS 放行(前后端分离开发需要)
 * - 全局 HTTP 响应 UTF-8 编码(避免 SSE / JSON 中文乱码)
 *
 * @author VanzyLiu
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 强制所有 String/JSON 响应使用 UTF-8 编码,
     * 即使某些浏览器/代理忽略了 Content-Type charset, 也能保证字节层面一致。
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> c : converters) {
            if (c instanceof StringHttpMessageConverter s) {
                s.setDefaultCharset(StandardCharsets.UTF_8);
                s.setSupportedMediaTypes(List.of(
                        new MediaType("text", "plain", StandardCharsets.UTF_8),
                        new MediaType("text", "event-stream", StandardCharsets.UTF_8),
                        new MediaType("text", "html", StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON
                ));
            } else if (c instanceof MappingJackson2HttpMessageConverter j) {
                j.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }
}

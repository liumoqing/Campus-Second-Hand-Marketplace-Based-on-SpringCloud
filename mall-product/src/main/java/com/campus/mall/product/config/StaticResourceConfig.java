package com.campus.mall.product.config;

import com.campus.mall.common.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源两处：
 * <ul>
 *   <li>`/files/**` → 本机上传目录（替代 MinIO 的对外读路径，见 05-工程改动记录 §7）</li>
 *   <li>`/static/**` → classpath:/static/（演示账号的头像，随 jar 一起打包）</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final LocalFileStorage storage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storage.root().toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
        // 演示头像：/static/avatar/alice.png → classpath:/static/avatar/alice.png
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}

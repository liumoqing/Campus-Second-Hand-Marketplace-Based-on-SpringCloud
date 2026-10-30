package com.campus.mall.user.controller;

import com.campus.mall.common.api.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Nacos Config 动态刷新验收端点：只暴露非敏感运行信息。 */
@RefreshScope
@RestController
@RequestMapping("/config")
public class RuntimeConfigController {

    @Value("${mall.runtime.environment:local}")
    private String environment;

    @Value("${mall.runtime.announcement:晴川二手市场运行正常}")
    private String announcement;

    @Value("${mall.runtime.refresh-version:0}")
    private int refreshVersion;

    @Value("${mall.runtime.central-marker:local-fallback}")
    private String centralMarker;

    @GetMapping("/runtime")
    public R<Map<String, Object>> runtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("environment", environment);
        data.put("announcement", announcement);
        data.put("refreshVersion", refreshVersion);
        data.put("centralMarker", centralMarker);
        return R.ok(data);
    }
}

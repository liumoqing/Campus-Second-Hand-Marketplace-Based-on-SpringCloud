package com.campus.mall.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.campus.mall.auth.vo.CaptchaVO;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * A1 图形验证码：hutool LineCaptcha，2 分钟过期、校验后立即删除（一次性）。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    private final StringRedisTemplate redis;

    @Value("${mall.captcha.expire-seconds:120}")
    private long expireSeconds;

    @Value("${mall.captcha.enabled:true}")
    private boolean enabled;

    public CaptchaVO generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String key = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(MallConstants.CACHE_CAPTCHA + key, captcha.getCode(),
                Duration.ofSeconds(expireSeconds));
        return new CaptchaVO(key, DATA_URI_PREFIX + captcha.getImageBase64());
    }

    /**
     * 校验并删除；开发期可通过 mall.captcha.enabled=false 关闭（01 §6.9 开关）。
     */
    public void verify(String captchaKey, String captchaCode) {
        if (!enabled) {
            return;
        }
        if (captchaKey == null || captchaCode == null) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
        String redisKey = MallConstants.CACHE_CAPTCHA + captchaKey;
        String expect = redis.opsForValue().get(redisKey);
        redis.delete(redisKey);
        if (expect == null || !expect.equalsIgnoreCase(captchaCode.trim())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
    }
}

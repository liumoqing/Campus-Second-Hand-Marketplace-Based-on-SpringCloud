package com.campus.mall.user.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.user.entity.User;
import com.campus.mall.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时解封（01 §9.2）：每小时把到期的封禁与上架限制自动恢复。
 * 永久封禁的 ban_until 是 9999-12-31，永远扫不到，符合预期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnbanTask {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "0 5 * * * ?")
    public void autoUnban() {
        LocalDateTime now = LocalDateTime.now();

        List<User> banned = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, MallConstants.STATUS_BANNED)
                .isNotNull(User::getBanUntil)
                .le(User::getBanUntil, now));
        for (User u : banned) {
            u.setStatus(MallConstants.STATUS_NORMAL);
            u.setBanUntil(null);
            u.setBanReason(null);
            u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
            userMapper.updateById(u);
            redis.delete(MallConstants.CACHE_USER_BANNED + u.getId());
            redis.delete(MallConstants.CACHE_USER_TV + u.getId());
            log.info("封禁到期自动解封：userId={}", u.getId());
        }

        List<User> publishBanned = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getCanPublish, 0)
                .isNotNull(User::getPublishBanUntil)
                .le(User::getPublishBanUntil, now));
        for (User u : publishBanned) {
            u.setCanPublish(1);
            u.setPublishBanUntil(null);
            userMapper.updateById(u);
            log.info("上架限制到期自动恢复：userId={}", u.getId());
        }
    }
}

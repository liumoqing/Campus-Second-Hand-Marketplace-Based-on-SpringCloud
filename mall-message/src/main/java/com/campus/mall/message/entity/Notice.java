package com.campus.mall.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知（02 §8.3 t_notice）：卡片管"聊天里看得到"，通知管"没进聊天也不漏"，两者同源双发。 */
@Data
@TableName("t_notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** SYSTEM / ORDER / REFUND */
    private String type;
    private String title;
    private String content;
    private String bizId;
    /** ORDER / PRODUCT / USER / NONE，前端据此决定点击跳哪 */
    private String bizType;
    private Integer readFlag;
    private LocalDateTime createTime;
}

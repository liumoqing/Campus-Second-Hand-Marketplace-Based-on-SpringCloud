package com.campus.mall.common.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务主表基类（02 §1 的"审计字段"约定）。
 * 注意：从属表（t_product_image / t_favorite / t_order_item / t_order_status_log / t_review）不要继承，
 * 它们按 02 §1 的列白名单只保留自己需要的字段。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableId
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}

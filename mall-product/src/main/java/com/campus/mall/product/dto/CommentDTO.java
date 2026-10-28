package com.campus.mall.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** P13 发表评论 / 回复（productId 从路径取）。 */
@Data
public class CommentDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 500, message = "评论 1~500 字")
    private String content;

    /** ≤3 张图 */
    private List<String> images;

    /** 0 或空=一级评论；否则是回复的父评论 id（必须是一级评论） */
    private Long parentId;
}

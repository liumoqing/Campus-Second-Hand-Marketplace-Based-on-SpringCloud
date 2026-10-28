package com.campus.mall.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** O7 双向评价：方向由调用者角色自动判定，客户端不能指定。 */
@Data
public class ReviewDTO {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1~5 星")
    @Max(value = 5, message = "评分 1~5 星")
    private Integer rating;

    @Size(max = 500, message = "评价最多 500 字")
    private String content;

    @Size(max = 3, message = "最多 3 张图")
    private List<String> images;

    private Boolean anonymous;
}

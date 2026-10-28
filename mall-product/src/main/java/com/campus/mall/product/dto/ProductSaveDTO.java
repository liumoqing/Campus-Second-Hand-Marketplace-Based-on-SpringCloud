package com.campus.mall.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** P4 发布 / P5 编辑 / P9 草稿发布共用请求体（03 §4）。 */
@Data
public class ProductSaveDTO {

    /** 标题 4~60 字；存草稿时只校验这一项 */
    @NotBlank(message = "标题不能为空")
    @Size(min = 4, max = 60, message = "标题 4~60 字")
    private String title;

    @Size(max = 2000, message = "描述最多 2000 字")
    private String description;

    private Long categoryId;

    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "原价不能为负")
    private BigDecimal originalPrice;

    @Min(value = 1, message = "成色 1~10")
    @Max(value = 10, message = "成色 1~10")
    private Integer conditionLevel;

    /** 图片 1~5 张，第一张作为封面 */
    private List<String> images;

    private String province;
    private String city;
    private String district;

    /** FREE_SHIPPING / DISTANCE / SELF_PICKUP */
    private String freightMode;

    /** 不传默认 ON_SALE；传 DRAFT 表示存草稿 */
    private String status;
}

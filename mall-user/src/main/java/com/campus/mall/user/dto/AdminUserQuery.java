package com.campus.mall.user.dto;

import lombok.Data;

/**
 * U12 后台用户列表查询参数（03 §3）。
 * canPublish 是 0/1 的 int（贴合前端下拉框），响应里的 canPublish 是 boolean。
 */
@Data
public class AdminUserQuery {

    private String keyword;
    private String role;
    private String status;
    private Integer canPublish;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}

package com.campus.mall.order.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.order.dto.OrderQuery;
import com.campus.mall.order.dto.RefundDtos;
import com.campus.mall.order.service.OrderService;
import com.campus.mall.order.service.RefundService;
import com.campus.mall.order.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台订单接口（O16~O18）：订单检索、数据概览、退款仲裁。
 */
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequireRole(com.campus.mall.common.constant.MallConstants.ROLE_ADMIN)
@RestController
@RequestMapping("/order/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final RefundService refundService;
    private final ReviewService reviewService;

    /** O16 后台订单列表 */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(OrderQuery query) {
        return R.ok(orderService.adminPage(query));
    }

    /** O17 数据概览：各状态订单数 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(orderService.adminStatistics());
    }

    /** O18 后台退款单 */
    @GetMapping("/refund/page")
    public R<PageResult<Map<String, Object>>> refundPage(@RequestParam(value = "status", required = false) String status,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(refundService.adminPage(status, pageNum, pageSize));
    }

    /** O18-1 管理员仲裁退款（与卖家处理同一入口，权限更高） */
    @PutMapping("/refund/{id}/handle")
    public R<Map<String, Object>> arbitrate(@PathVariable("id") Long id,
                                            @Valid @RequestBody RefundDtos.Handle dto) {
        return R.ok(refundService.handle(UserContext.requireUserId(), id, dto));
    }

    /** O18-2 后台评价列表 */
    @GetMapping("/review/page")
    public R<PageResult<Map<String, Object>>> reviews(@RequestParam(value = "productId", required = false) Long productId,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.adminPage(pageNum, pageSize, productId));
    }
}

package com.campus.mall.order.controller;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.order.dto.OrderCreateDTO;
import com.campus.mall.order.dto.OrderQuery;
import com.campus.mall.order.dto.RefundDtos;
import com.campus.mall.order.dto.ReviewDTO;
import com.campus.mall.order.service.OrderService;
import com.campus.mall.order.service.RefundService;
import com.campus.mall.order.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单对外接口（O1~O15），全部要求登录；数据归属在 service 里再校验一次。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;
    private final RefundService refundService;

    /** O1 下单（金额与运费全部服务端算） */
    @RequireRole
    @PostMapping("/create")
    public R<Map<String, Object>> create(@Valid @RequestBody OrderCreateDTO dto) {
        return R.ok(orderService.create(UserContext.requireUserId(), dto));
    }

    /** O2 我的订单列表 */
    @RequireRole
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(OrderQuery query) {
        return R.ok(orderService.page(UserContext.requireUserId(), query));
    }

    /** O3 订单详情 */
    @RequireRole
    @GetMapping("/{orderNo}")
    public R<Map<String, Object>> detail(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.detail(UserContext.requireUserId(), orderNo));
    }

    /** O4 取消（仅待付款） */
    @RequireRole
    @PutMapping("/{orderNo}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable("orderNo") String orderNo,
                                        @RequestParam(value = "reason", required = false) String reason) {
        return R.ok(orderService.cancel(UserContext.requireUserId(), orderNo, reason));
    }

    /** O5 发货（仅卖家） */
    @RequireRole
    @PutMapping("/{orderNo}/ship")
    public R<Map<String, Object>> ship(@PathVariable("orderNo") String orderNo,
                                       @RequestParam(value = "logisticsNo", required = false) String logisticsNo) {
        return R.ok(orderService.ship(UserContext.requireUserId(), orderNo, logisticsNo));
    }

    /** O6 确认收货（仅买家） */
    @RequireRole
    @PutMapping("/{orderNo}/receive")
    public R<Map<String, Object>> receive(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.receive(UserContext.requireUserId(), orderNo));
    }

    /** O7 双向评价（方向按调用者角色自动判定） */
    @RequireRole
    @PostMapping("/{orderNo}/review")
    public R<Map<String, Object>> review(@PathVariable("orderNo") String orderNo,
                                         @Valid @RequestBody ReviewDTO dto) {
        return R.ok(reviewService.create(UserContext.requireUserId(), orderNo, dto));
    }

    /** O8 卖方/买方回复评价 */
    @RequireRole
    @PutMapping("/review/{id:\\d+}/reply")
    public R<Map<String, Object>> reply(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String content = body.get("replyContent") != null ? body.get("replyContent") : body.get("content");
        return R.ok(reviewService.reply(UserContext.requireUserId(), id, content));
    }

    /** O9 我写的评价 */
    @RequireRole
    @GetMapping("/review/mine")
    public R<PageResult<Map<String, Object>>> myReviews(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.mine(UserContext.requireUserId(), pageNum, pageSize));
    }

    /** O10 我收到的评价 */
    @RequireRole
    @GetMapping("/review/received")
    public R<PageResult<Map<String, Object>>> receivedReviews(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.received(UserContext.requireUserId(), pageNum, pageSize));
    }

    /** O11 某用户收到的评价（公开可看，只取买家评卖家方向） */
    @GetMapping("/review/user/{userId:\\d+}")
    public R<PageResult<Map<String, Object>>> userReviews(@PathVariable("userId") Long userId,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.ofUser(userId, pageNum, pageSize));
    }

    /** O12 申请退款/售后（orderNo 在路径上） */
    @RequireRole
    @PostMapping("/{orderNo}/refund")
    public R<Map<String, Object>> applyRefund(@PathVariable("orderNo") String orderNo,
                                              @Valid @RequestBody RefundDtos.Apply dto) {
        dto.setOrderNo(orderNo);
        return R.ok(refundService.apply(UserContext.requireUserId(), dto));
    }

    /** O13 卖家处理退款 */
    @RequireRole
    @PutMapping("/refund/{id}/handle")
    public R<Map<String, Object>> handleRefund(@PathVariable("id") Long id,
                                               @Valid @RequestBody RefundDtos.Handle dto) {
        return R.ok(refundService.handle(UserContext.requireUserId(), id, dto));
    }

    /** O14 我的退款单（role=bought 默认 / sold 卖家视角） */
    @RequireRole
    @GetMapping("/refund/page")
    public R<PageResult<Map<String, Object>>> refundPage(@RequestParam(value = "role", required = false) String role,
                                                         @RequestParam(value = "status", required = false) String status,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(refundService.page(UserContext.requireUserId(), role, status, pageNum, pageSize));
    }

    /** O15 字典：订单状态、退款状态、角色视角 */
    @GetMapping("/dict")
    public R<Map<String, Object>> dict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderStatus", List.of(
                Map.of("code", "UNPAID", "text", "待付款"),
                Map.of("code", "UNSHIPPED", "text", "待发货"),
                Map.of("code", "UNRECEIVED", "text", "待收货"),
                Map.of("code", "COMPLETED", "text", "已完成"),
                Map.of("code", "CANCELLED", "text", "已取消"),
                Map.of("code", "REFUNDING", "text", "退款中"),
                Map.of("code", "REFUNDED", "text", "已退款")));
        data.put("refundStatus", List.of(
                Map.of("code", "APPLYING", "text", "待处理"),
                Map.of("code", "AGREED", "text", "已同意"),
                Map.of("code", "REJECTED", "text", "已拒绝"),
                Map.of("code", "REFUNDED", "text", "已退款"),
                Map.of("code", "CLOSED", "text", "已关闭")));
        data.put("roles", List.of(
                Map.of("code", "bought", "text", "我买到的"),
                Map.of("code", "sold", "text", "我卖出的")));
        return R.ok(data);
    }
}

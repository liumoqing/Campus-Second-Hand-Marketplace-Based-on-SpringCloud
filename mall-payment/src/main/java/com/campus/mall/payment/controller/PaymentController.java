package com.campus.mall.payment.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.payment.dto.MockCallbackDTO;
import com.campus.mall.payment.dto.PayDTO;
import com.campus.mall.payment.service.PaymentService;
import com.campus.mall.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 支付接口 Y1~Y6（03 §6）。 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Y1 支付（一步模拟，同步返回 SUCCESS） */
    @PostMapping("/pay")
    @RequireRole
    public R<PaymentVO> pay(@RequestBody @Valid PayDTO dto) {
        return R.ok(paymentService.pay(UserContext.requireUserId(), dto.getOrderNo()));
    }

    /** Y2 支付单详情 */
    @GetMapping("/{payNo}")
    @RequireRole
    public R<PaymentVO> detail(@PathVariable String payNo) {
        return R.ok(paymentService.detail(payNo, UserContext.requireUserId(), UserContext.isAdmin()));
    }

    /** Y3 模拟回调（公开，靠验签；留给支付宝沙箱） */
    @PostMapping("/callback/mock")
    public R<PaymentVO> callback(@RequestBody @Valid MockCallbackDTO dto) {
        return R.ok(paymentService.callback(dto));
    }

    /** Y5 字典 */
    @GetMapping("/dict")
    @RequireRole
    public R<Map<String, List<Map<String, Object>>>> dict() {
        return R.ok(paymentService.dict());
    }

    /** Y6 后台支付单分页 */
    @GetMapping("/admin/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<PageResult<PaymentVO>> adminPage(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(paymentService.adminPage(pageNum, pageSize, status, channel, keyword));
    }
}

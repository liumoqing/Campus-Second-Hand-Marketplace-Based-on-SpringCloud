package com.campus.mall.product.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.common.storage.LocalFileStorage;
import com.campus.mall.product.service.InnerProductService;
import com.campus.mall.product.vo.ProductVos.InnerProductVO;
import com.campus.mall.product.vo.ProductVos.ProductBriefVO;
import com.campus.mall.product.vo.ProductVos.StatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 内部接口（P27~P34），只被其他服务调用，不过网关；鉴权靠 X-Internal-Token（common 里的 InternalTokenFilter）。
 */
@RestController
@RequestMapping("/inner/product")
@RequiredArgsConstructor
public class InnerProductController {

    private final InnerProductService innerProductService;
    private final LocalFileStorage storage;

    /** P27 商品内部视图 */
    @GetMapping("/{id:\\d+}")
    public R<InnerProductVO> view(@PathVariable("id") Long id) {
        return R.ok(innerProductService.view(id));
    }

    /** P28 批量查询 */
    @PostMapping("/batch")
    public R<List<ProductBriefVO>> batch(@RequestBody Map<String, List<Long>> body) {
        return R.ok(innerProductService.batch(body.get("ids")));
    }

    /** P29 扣库存并置 LOCKED（订单服务的 Seata 分支） */
    @PostMapping("/stock/deduct")
    public R<Void> deduct(@RequestBody StockBody body) {
        innerProductService.deduct(body.productId(), body.orderNo());
        return R.ok(null);
    }

    /** P30 回补为 ON_SALE */
    @PostMapping("/stock/restore")
    public R<Void> restore(@RequestBody StockBody body) {
        innerProductService.restore(body.productId(), body.orderNo());
        return R.ok(null);
    }

    /** P31 置 SOLD */
    @PostMapping("/sold")
    public R<Void> sold(@RequestBody StockBody body) {
        innerProductService.sold(body.productId(), body.orderNo());
        return R.ok(null);
    }

    /** P32 浏览量 +1（异步调用，不抛业务异常） */
    @GetMapping("/view/{id:\\d+}")
    public R<Void> viewCount(@PathVariable("id") Long id) {
        innerProductService.incrView(id);
        return R.ok(null);
    }

    /** P33 头像/商品图转存（auth 的 A8 走这里） */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(value = "bizType", defaultValue = "avatar") String bizType) {
        String url = storage.save(file, bizType, "avatar".equals(bizType) ? 2 : 5);
        return R.ok(Map.of("url", url));
    }

    /** P34 卖家商品计数 */
    @GetMapping("/statistics/{sellerId:\\d+}")
    public R<StatisticsVO> statistics(@PathVariable("sellerId") Long sellerId) {
        return R.ok(innerProductService.statistics(sellerId));
    }

    public record StockBody(Long productId, String orderNo) {
    }
}

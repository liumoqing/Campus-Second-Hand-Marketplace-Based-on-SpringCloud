package com.campus.mall.product.controller;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.common.storage.LocalFileStorage;
import com.campus.mall.product.dto.ProductQuery;
import com.campus.mall.product.dto.ProductSaveDTO;
import com.campus.mall.product.service.ProductService;
import com.campus.mall.product.vo.ProductVos.ProductCardVO;
import com.campus.mall.product.vo.ProductVos.ProductDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 商品（P1~P11）。游客只能看列表前 20 条，详情要求登录（01 §7.2 白名单与 03 §1.10）。
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final LocalFileStorage storage;

    /** P1 首页列表：公开接口，游客由服务端强制第 1 页 20 条 */
    @GetMapping("/page")
    public R<Map<String, Object>> page(ProductQuery query) {
        boolean guest = UserContext.userId() == null;
        return R.ok(productService.page(query, guest));
    }

    /** P2 详情：要求登录 */
    @GetMapping("/{id:\\d+}")
    @RequireRole
    public R<ProductDetailVO> detail(@PathVariable("id") Long id) {
        return R.ok(productService.detail(id, UserContext.requireUserId()));
    }

    /** P3 相似推荐 */
    @GetMapping("/{id:\\d+}/similar")
    @RequireRole
    public R<List<ProductCardVO>> similar(@PathVariable("id") Long id,
                                          @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return R.ok(productService.similar(id, limit));
    }

    /** P4 发布：status 不传或传 ON_SALE 直接上架，传 DRAFT 存草稿 */
    @PostMapping
    @RequireRole
    public R<Map<String, Object>> create(@Valid @RequestBody ProductSaveDTO dto) {
        return R.ok(productService.create(UserContext.requireUserId(), dto));
    }

    /** P5 编辑 */
    @PutMapping("/{id:\\d+}")
    @RequireRole
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProductSaveDTO dto) {
        return R.ok(productService.update(UserContext.requireUserId(), id, dto));
    }

    /** P6 删除 */
    @DeleteMapping("/{id:\\d+}")
    @RequireRole
    public R<Boolean> delete(@PathVariable("id") Long id) {
        return R.ok(productService.delete(UserContext.requireUserId(), id));
    }

    /** P7 本人下架 */
    @PutMapping("/{id:\\d+}/off-shelf")
    @RequireRole
    public R<Boolean> offShelf(@PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return R.ok(productService.offShelf(UserContext.requireUserId(), id, reason));
    }

    /** P8 重新上架 */
    @PutMapping("/{id:\\d+}/on-shelf")
    @RequireRole
    public R<Boolean> onShelf(@PathVariable("id") Long id) {
        return R.ok(productService.onShelf(UserContext.requireUserId(), id));
    }

    /** P9 草稿发布（body 可选：草稿里没填全的字段可以在这里补齐再发布） */
    @PutMapping("/{id:\\d+}/publish")
    @RequireRole
    public R<Map<String, Object>> publish(@PathVariable("id") Long id,
                                          @RequestBody(required = false) ProductSaveDTO dto) {
        return R.ok(productService.publishDraft(UserContext.requireUserId(), id, dto));
    }

    /** P10 我的商品 / 草稿箱 */
    @GetMapping("/my/page")
    @RequireRole
    public R<PageResult<ProductCardVO>> myPage(ProductQuery query) {
        return R.ok(productService.myPage(UserContext.requireUserId(), query));
    }

    /** P18 枚举字典 */
    @GetMapping("/dict")
    @RequireRole
    public R<Map<String, Object>> dict() {
        return R.ok(Map.of(
                "conditionLevel", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                "freightMode", List.of("FREE_SHIPPING", "DISTANCE", "SELF_PICKUP"),
                "sort", List.of("new", "oldest", "price_asc", "price_desc", "hot", "nearby"),
                "productStatus", List.of("DRAFT", "ON_SALE", "LOCKED", "SOLD", "OFF_SHELF")
        ));
    }

    /** P11 上传商品图（multipart：file + bizType） */
    @PostMapping("/upload")
    @RequireRole
    public R<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(value = "bizType", defaultValue = "product") String bizType) {
        if (!List.of("product", "avatar", "review", "chat").contains(bizType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 只允许 product/avatar/review/chat");
        }
        String url = storage.save(file, bizType, "avatar".equals(bizType) ? 2 : 5);
        return R.ok(Map.of("url", url));
    }
}

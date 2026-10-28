package com.campus.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.product.client.OrderClient;
import com.campus.mall.product.client.UserClient;
import com.campus.mall.product.dto.ProductQuery;
import com.campus.mall.product.dto.ProductSaveDTO;
import com.campus.mall.product.entity.Favorite;
import com.campus.mall.product.entity.Product;
import com.campus.mall.product.entity.ProductImage;
import com.campus.mall.product.mapper.FavoriteMapper;
import com.campus.mall.product.mapper.ProductImageMapper;
import com.campus.mall.product.mapper.ProductMapper;
import com.campus.mall.product.vo.ProductVos.ProductCardVO;
import com.campus.mall.product.vo.ProductVos.ProductDetailVO;
import com.campus.mall.product.vo.ProductVos.SellerVO;
import com.campus.mall.product.vo.ProductVos.StatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品主流程（P1~P10）：发布即上架（无审核）、草稿只对作者可见、一件一单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int GUEST_PAGE_SIZE = 20;

    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final FavoriteMapper favoriteMapper;
    private final CategoryService categoryService;
    private final UserClient userClient;
    private final OrderClient orderClient;

    // ---------------- P1 首页列表 ----------------

    public Map<String, Object> page(ProductQuery q, boolean guest) {
        int pageNum = guest ? 1 : Math.max(1, q.getPageNum() == null ? 1 : q.getPageNum());
        int pageSize = guest ? GUEST_PAGE_SIZE : Math.min(50, Math.max(1, q.getPageSize() == null ? 20 : q.getPageSize()));

        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .eq(q.getSellerId() != null, Product::getSellerId, q.getSellerId())
                .eq(StringUtils.hasText(q.getCity()), Product::getCity, q.getCity())
                .ge(q.getConditionLevel() != null, Product::getConditionLevel, q.getConditionLevel())
                .ge(q.getPriceMin() != null, Product::getPrice, q.getPriceMin())
                .le(q.getPriceMax() != null, Product::getPrice, q.getPriceMax());
        if (q.getCategoryId() != null) {
            w.eq(Product::getCategoryId, q.getCategoryId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            w.and(x -> x.like(Product::getTitle, q.getKeyword()).or().like(Product::getDescription, q.getKeyword()));
        }
        applySort(w, q.getSort());
        Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), w);

        Map<String, Object> data = new LinkedHashMap<>();
        List<ProductCardVO> cards = toCards(page.getRecords());
        data.put("records", cards);
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        data.put("pages", guest ? 1 : page.getPages());
        data.put("guestLimited", guest);
        return data;
    }

    private void applySort(LambdaQueryWrapper<Product> w, String sort) {
        String s = sort == null ? "new" : sort;
        switch (s) {
            case "oldest" -> w.orderByAsc(Product::getCreateTime);
            case "price_asc" -> w.orderByAsc(Product::getPrice);
            case "price_desc" -> w.orderByDesc(Product::getPrice);
            case "hot" -> w.orderByDesc(Product::getViewCount).orderByDesc(Product::getCreateTime);
            default -> w.orderByDesc(Product::getCreateTime);
        }
    }

    // ---------------- P2 详情 ----------------

    public ProductDetailVO detail(Long id, Long currentUserId) {
        Product p = requireProduct(id);
        boolean owner = currentUserId != null && currentUserId.equals(p.getSellerId());
        boolean admin = UserContext.isAdmin();
        if ("DRAFT".equals(p.getStatus()) && !owner && !admin) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setCategoryId(p.getCategoryId());
        vo.setCategoryName(categoryService.nameOf(p.getCategoryId()));
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setConditionLevel(p.getConditionLevel());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setImages(imagesOf(p.getId()));
        vo.setProvince(p.getProvince());
        vo.setCity(p.getCity());
        vo.setDistrict(p.getDistrict());
        vo.setFreightMode(p.getFreightMode());
        vo.setFreightHint(freightHint(p.getFreightMode()));
        vo.setStatus(p.getStatus());
        if (owner || admin) {
            vo.setOffShelfReason(p.getOffShelfReason());
        }
        vo.setViewCount(p.getViewCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setCommentCount(p.getCommentCount());
        vo.setCreateTime(p.getCreateTime());
        vo.setFavorited(currentUserId != null && isFavorited(currentUserId, p.getId()));
        vo.setSeller(seller(p.getSellerId()));
        // 浏览量自增（失败不影响详情）
        try {
            productMapper.incrView(p.getId());
        } catch (Exception e) {
            log.debug("浏览量自增失败：id={}", p.getId());
        }
        return vo;
    }

    private SellerVO seller(Long sellerId) {
        SellerVO s = new SellerVO();
        s.setId(sellerId);
        Map<String, Object> u = safeBrief(sellerId);
        if (u != null) {
            s.setUsername(str(u.get("username")));
            s.setNickname(str(u.get("nickname")));
            s.setAvatar(str(u.get("avatar")));
            s.setCity(str(u.get("city")));
            s.setSignature(str(u.get("signature")));
            s.setCreditScore(intOf(u.get("creditScore"), 100));
            s.setLastLoginTime(timeOf(u.get("lastLoginTime")));
            LocalDateTime created = timeOf(u.get("createTime"));
            s.setJoinedAt(created);
            s.setRegisterDays(created == null ? 0L
                    : Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(created, LocalDateTime.now())));
        }
        Map<String, Object> st = safeOrderStatistics(sellerId);
        s.setSoldCount(intOf(st == null ? null : st.get("soldCount"), 0));
        Object avg = st == null ? null : st.get("avgRating");
        s.setAvgRating(avg == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(avg)).setScale(1, RoundingMode.HALF_UP));
        s.setReviewCount(intOf(st == null ? null : st.get("reviewCount"), 0));
        s.setOnSaleCount(countOnSale(sellerId));
        return s;
    }

    // ---------------- P3 我的商品 / P10 草稿箱 ----------------

    public PageResult<ProductCardVO> myPage(Long sellerId, ProductQuery q) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                .orderByDesc(Product::getCreateTime);
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Product::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            w.like(Product::getTitle, q.getKeyword());
        }
        Page<Product> page = productMapper.selectPage(
                new Page<>(Math.max(1, q.getPageNum() == null ? 1 : q.getPageNum()), Math.min(50, Math.max(1, q.getPageSize() == null ? 10 : q.getPageSize()))), w);
        return PageResult.from(page.convert(p -> toCards(List.of(p)).get(0)));
    }

    public PageResult<ProductCardVO> drafts(Long sellerId, ProductQuery q) {
        q.setStatus("DRAFT");
        return myPage(sellerId, q);
    }

    // ---------------- P4 发布 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long sellerId, ProductSaveDTO dto) {
        boolean draft = "DRAFT".equals(dto.getStatus());
        // 上架权限校验先于业务校验：被限制的用户点"发布"要先看到 30009，而不是"没传图片"
        if (!draft) {
            checkPublishPermission(sellerId);
        }
        validate(dto, draft);
        if (!draft) {
            categoryService.requireEnabled(dto.getCategoryId());
        }
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setStatus(draft ? "DRAFT" : "ON_SALE");
        applyFields(p, dto);
        if (draft) {
            fillDraftDefaults(p);
        }
        p.setStock(1);
        p.setVersion(0);
        p.setViewCount(0);
        p.setFavoriteCount(0);
        p.setCommentCount(0);
        productMapper.insert(p);
        saveImages(p.getId(), dto.getImages());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", p.getId());
        data.put("status", p.getStatus());
        return data;
    }

    // ---------------- P5 编辑 ----------------

    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long sellerId, Long id, ProductSaveDTO dto) {
        Product p = requireOwn(sellerId, id);
        if ("SOLD".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "已售出的商品不能再编辑");
        }
        // 按 03 §P5 的约定：P5 只改内容，**不改状态**（上下架走 P7/P8、草稿发布走 P9）。
        // 校验强度：草稿只校验标题（其余字段可后补，P9 发布时统一兜底）；在售商品走「局部校验」，
        // 只校验前端真传了的字段，避免只改标题也被要求重传图片。
        boolean draft = "DRAFT".equals(p.getStatus());
        if ("LOCKED".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "交易中的商品不能编辑");
        }
        if (draft) {
            validate(dto, true);
        } else {
            validatePartial(dto);
            Long cid = dto.getCategoryId() != null ? dto.getCategoryId() : p.getCategoryId();
            categoryService.requireEnabled(cid);
            checkPublishPermission(sellerId);
        }
        applyFields(p, dto);
        if (draft) {
            fillDraftDefaults(p);
        }
        productMapper.updateById(p);
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            imageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id));
            saveImages(id, dto.getImages());
        }
        return true;
    }

    // ---------------- P6 本人下架 / P7 本人上架 ----------------

    public boolean offShelf(Long sellerId, Long id, String reason) {
        Product p = requireOwn(sellerId, id);
        if ("SOLD".equals(p.getStatus()) || "LOCKED".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该状态的商品不能下架");
        }
        p.setStatus("OFF_SHELF");
        p.setOffShelfReason(StringUtils.hasText(reason) ? reason : "本人下架");
        productMapper.updateById(p);
        categoryService.clearCache();
        return true;
    }

    public boolean onShelf(Long sellerId, Long id) {
        Product p = requireOwn(sellerId, id);
        if (!"OFF_SHELF".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "只有已下架的商品可以上架");
        }
        checkPublishPermission(sellerId);
        p.setStatus("ON_SALE");
        p.setOffShelfReason(null);
        productMapper.updateById(p);
        productMapper.markOnSale(id);
        return true;
    }

    // ---------------- P8 删除 ----------------

    public boolean delete(Long sellerId, Long id) {
        Product p = requireOwn(sellerId, id);
        if ("LOCKED".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "商品被订单锁定中，不能删除");
        }
        productMapper.deleteById(id);
        imageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id));
        return true;
    }

    // ---------------- P9 草稿发布 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishDraft(Long sellerId, Long id, ProductSaveDTO dto) {
        Product p = requireOwn(sellerId, id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "不是草稿状态");
        }
        checkPublishPermission(sellerId);
        // 草稿只存了标题时，前端在"发布"这一步把剩下字段一起提交上来，这里先补齐再校验
        if (dto != null) {
            validate(dto, false);
            applyFields(p, dto);
            if (dto.getImages() != null && !dto.getImages().isEmpty()) {
                imageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id));
                saveImages(id, dto.getImages());
            }
        }
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(p.getCoverUrl())) {
            missing.add("images");
        }
        if (p.getCategoryId() == null || p.getCategoryId() == 0L) {
            missing.add("categoryId");
        }
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            missing.add("price");
        }
        if (!StringUtils.hasText(p.getProvince()) || !StringUtils.hasText(p.getCity())) {
            missing.add("region");
        }
        if (!StringUtils.hasText(p.getFreightMode())) {
            missing.add("freightMode");
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.DRAFT_INCOMPLETE, "草稿还缺：" + String.join("、", missing), Map.of("missing", missing));
        }
        categoryService.requireEnabled(p.getCategoryId());
        p.setStatus("ON_SALE");
        productMapper.updateById(p);
        // updateById 会忽略 null 字段，下架原因得单独清掉
        productMapper.markOnSale(id);
        return Map.of("productId", p.getId(), "status", p.getStatus());
    }

    // ---------------- 工具 ----------------

    public Product requireProduct(Long id) {
        Product p = id == null ? null : productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return p;
    }

    private Product requireOwn(Long sellerId, Long id) {
        Product p = requireProduct(id);
        if (!Objects.equals(p.getSellerId(), sellerId)) {
            throw new BizException(ErrorCode.PRODUCT_NOT_OWNER);
        }
        return p;
    }

    /** 上架权限闸门：30009 + 解封时间（03 §4 P4） */
    private void checkPublishPermission(Long sellerId) {
        Map<String, Object> u = safeBrief(sellerId);
        if (u == null) {
            return;
        }
        Object canPublish = u.get("canPublish");
        boolean allowed = !(canPublish instanceof Boolean b) || b;
        LocalDateTime until = timeOf(u.get("publishBanUntil"));
        boolean expired = until != null && until.isBefore(LocalDateTime.now());
        if (!allowed && !expired) {
            throw new BizException(ErrorCode.PUBLISH_FORBIDDEN,
                    "您因违反用户规则暂时无法上架商品，解封时间" + fmt(until),
                    Map.of("publishBanUntil", until));
        }
    }

    private void validate(ProductSaveDTO dto, boolean draft) {
        if (!StringUtils.hasText(dto.getTitle()) || dto.getTitle().length() < 4 || dto.getTitle().length() > 60) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题 4~60 字");
        }
        if (draft) {
            return;
        }
        if (dto.getImages() == null || dto.getImages().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少上传 1 张商品图");
        }
        if (dto.getImages().size() > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品图最多 5 张");
        }
        if (dto.getCategoryId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择分类");
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "价格必须大于 0");
        }
        if (!StringUtils.hasText(dto.getProvince()) || !StringUtils.hasText(dto.getCity())
                || !StringUtils.hasText(dto.getDistrict())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择发货地（省/市/区）");
        }
        if (!StringUtils.hasText(dto.getFreightMode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择运费方式");
        }
        if (!List.of("FREE_SHIPPING", "DISTANCE", "SELF_PICKUP").contains(dto.getFreightMode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "运费方式不合法");
        }
    }

    /**
     * P5 局部编辑用：只校验"前端真的传了"的字段，没传的沿用库里旧值（03 §P5「可只传要改的」）。
     */
    private void validatePartial(ProductSaveDTO dto) {
        if (dto.getTitle() != null
                && (!StringUtils.hasText(dto.getTitle()) || dto.getTitle().length() < 4 || dto.getTitle().length() > 60)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题 4~60 字");
        }
        if (dto.getImages() != null) {
            if (dto.getImages().isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "至少上传 1 张商品图");
            }
            if (dto.getImages().size() > 5) {
                throw new BizException(ErrorCode.PARAM_INVALID, "商品图最多 5 张");
            }
        }
        if (dto.getPrice() != null && dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "价格必须大于 0");
        }
        if (dto.getFreightMode() != null
                && !List.of("FREE_SHIPPING", "DISTANCE", "SELF_PICKUP").contains(dto.getFreightMode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "运费方式不合法");
        }
    }

    /**
     * 只覆盖前端真正传了值的字段：草稿/编辑时前端可能不带价格、分类、地区，
     * 这些列在 t_product 里是 NOT NULL，直接 set(null) 会撞 SQL 报 500，所以按「非空才写」处理。
     */
    private void applyFields(Product p, ProductSaveDTO dto) {
        if (dto.getTitle() != null) p.setTitle(dto.getTitle());
        if (dto.getDescription() != null) p.setDescription(dto.getDescription());
        if (dto.getCategoryId() != null) p.setCategoryId(dto.getCategoryId());
        if (dto.getPrice() != null) p.setPrice(dto.getPrice());
        if (dto.getOriginalPrice() != null) p.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getConditionLevel() != null) p.setConditionLevel(dto.getConditionLevel());
        if (dto.getProvince() != null) p.setProvince(dto.getProvince());
        if (dto.getCity() != null) p.setCity(dto.getCity());
        if (dto.getDistrict() != null) p.setDistrict(dto.getDistrict());
        if (dto.getFreightMode() != null) p.setFreightMode(dto.getFreightMode());
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            p.setCoverUrl(dto.getImages().get(0));
        }
    }

    /**
     * 草稿允许只填标题，但 t_product 的 category_id/price/cover_url/省市区都是 NOT NULL 且无默认值，
     * 这里给占位值；等 P9 发布时再要求补齐（缺项由 publishDraft 报 30004）。
     */
    private void fillDraftDefaults(Product p) {
        if (p.getCategoryId() == null) {
            p.setCategoryId(0L);
        }
        if (p.getPrice() == null) {
            p.setPrice(BigDecimal.ZERO);
        }
        if (!StringUtils.hasText(p.getCoverUrl())) {
            p.setCoverUrl("");
        }
        if (!StringUtils.hasText(p.getProvince())) {
            p.setProvince("");
        }
        if (!StringUtils.hasText(p.getCity())) {
            p.setCity("");
        }
        if (!StringUtils.hasText(p.getDistrict())) {
            p.setDistrict("");
        }
        if (!StringUtils.hasText(p.getFreightMode())) {
            p.setFreightMode("DISTANCE");
        }
    }

    private void saveImages(Long productId, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int i = 0;
        for (String url : urls) {
            ProductImage img = new ProductImage();
            img.setProductId(productId);
            img.setUrl(url);
            img.setSort(i++);
            img.setCreateTime(LocalDateTime.now());
            imageMapper.insert(img);
        }
    }

    private List<String> imagesOf(Long productId) {
        List<ProductImage> list = imageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId)
                .orderByAsc(ProductImage::getSort));
        List<String> urls = new ArrayList<>();
        for (ProductImage i : list) {
            urls.add(i.getUrl());
        }
        return urls;
    }

    private boolean isFavorited(Long userId, Long productId) {
        Long n = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        return n != null && n > 0;
    }

    /** 列表卡片：批量取卖家昵称头像，避免 N+1 */
    private List<ProductCardVO> toCards(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> sellerIds = products.stream().map(Product::getSellerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Map<String, Object>> userMap = new LinkedHashMap<>();
        if (!sellerIds.isEmpty()) {
            try {
                R<List<Map<String, Object>>> r = userClient.batch(sellerIds);
                if (r != null && r.getData() != null) {
                    for (Map<String, Object> m : r.getData()) {
                        userMap.put(longOf(m.get("id")), m);
                    }
                }
            } catch (Exception e) {
                log.warn("批量取卖家信息失败，降级为空：{}", e.getMessage());
            }
        }
        List<ProductCardVO> cards = new ArrayList<>(products.size());
        for (Product p : products) {
            ProductCardVO c = new ProductCardVO();
            c.setId(p.getId());
            c.setTitle(p.getTitle());
            c.setCoverUrl(p.getCoverUrl());
            c.setPrice(p.getPrice());
            c.setOriginalPrice(p.getOriginalPrice());
            c.setConditionLevel(p.getConditionLevel());
            c.setCity(p.getCity());
            c.setFreightMode(p.getFreightMode());
            c.setStatus(p.getStatus());
            c.setSellerId(p.getSellerId());
            c.setFavoriteCount(p.getFavoriteCount());
            c.setCommentCount(p.getCommentCount());
            c.setViewCount(p.getViewCount());
            c.setCreateTime(p.getCreateTime());
            Map<String, Object> u = userMap.get(p.getSellerId());
            if (u != null) {
                c.setSellerNickname(str(u.get("nickname")));
                c.setSellerAvatar(str(u.get("avatar")));
            }
            cards.add(c);
        }
        return cards;
    }

    // ---------------- P24 商品管理列表（ADMIN） ----------------

    public PageResult<Map<String, Object>> adminPage(ProductQuery q) {
        int pageNum = Math.max(1, q.getPageNum() == null ? 1 : q.getPageNum());
        int pageSize = Math.min(100, Math.max(1, q.getPageSize() == null ? 20 : q.getPageSize()));
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(StringUtils.hasText(q.getStatus()), Product::getStatus, q.getStatus())
                .eq(q.getCategoryId() != null, Product::getCategoryId, q.getCategoryId())
                .like(StringUtils.hasText(q.getKeyword()), Product::getTitle, q.getKeyword())
                .orderByDesc(Product::getCreateTime);
        if (q.getSellerId() != null) {
            w.eq(Product::getSellerId, q.getSellerId());
        }
        Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Product p : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("title", p.getTitle());
            row.put("coverUrl", p.getCoverUrl());
            row.put("price", p.getPrice());
            row.put("status", p.getStatus());
            row.put("sellerId", p.getSellerId());
            Map<String, Object> u = safeBrief(p.getSellerId());
            row.put("sellerNickname", u == null ? null : u.get("nickname"));
            row.put("categoryName", categoryService.nameOf(p.getCategoryId()));
            row.put("viewCount", p.getViewCount());
            row.put("favoriteCount", p.getFavoriteCount());
            row.put("offShelfReason", p.getOffShelfReason());
            row.put("createTime", p.getCreateTime());
            records.add(row);
        }
        return PageResult.from(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** P25 管理员强制下架：不改卖家自己的下架原因，写管理员原因并清理曝光 */
    public boolean adminOffShelf(Long id, String reason) {
        Product p = requireProduct(id);
        if ("SOLD".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "已售出的商品不能强制下架");
        }
        if ("OFF_SHELF".equals(p.getStatus())) {
            return true;
        }
        p.setStatus("OFF_SHELF");
        p.setOffShelfReason(StringUtils.hasText(reason) ? reason : "违反平台规则，已被管理员下架");
        productMapper.updateById(p);
        categoryService.clearCache();
        log.info("管理员强制下架商品：id={}, reason={}", id, p.getOffShelfReason());
        return true;
    }

    public Map<String, Object> adminStatistics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onSale", countAll("ON_SALE"));
        data.put("draft", countAll("DRAFT"));
        data.put("locked", countAll("LOCKED"));
        data.put("sold", countAll("SOLD"));
        data.put("offShelf", countAll("OFF_SHELF"));
        data.put("total", productMapper.selectCount(null));
        return data;
    }

    private Long countAll(String status) {
        return productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStatus, status));
    }

    private Integer countOnSale(Long sellerId) {
        Long n = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId).eq(Product::getStatus, "ON_SALE"));
        return n == null ? 0 : n.intValue();
    }

    /** P3 相似推荐：同分类优先，再同城，最多 limit 条，排除自己、下架与售出 */
    public List<ProductCardVO> similar(Long productId, int limit) {
        Product p = requireProduct(productId);
        int size = Math.min(8, Math.max(1, limit));
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .ne(Product::getId, productId)
                .eq(p.getCategoryId() != null, Product::getCategoryId, p.getCategoryId());
        List<Product> list = productMapper.selectList(w.orderByDesc(Product::getCreateTime).last("LIMIT " + size));
        if (list.isEmpty() && p.getCity() != null) {
            list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, "ON_SALE")
                    .ne(Product::getId, productId)
                    .eq(Product::getCity, p.getCity())
                    .orderByDesc(Product::getCreateTime)
                    .last("LIMIT " + size));
        }
        return toCards(list);
    }

    public Map<String, Object> briefOf(Long userId) {
        return safeBrief(userId);
    }

    private Map<String, Object> safeBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            R<Map<String, Object>> r = userClient.brief(userId);
            return r == null ? null : r.getData();
        } catch (Exception e) {
            log.warn("调用 mall-user 取用户信息失败：userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> safeOrderStatistics(Long userId) {
        try {
            R<Map<String, Object>> r = orderClient.statistics(userId);
            return r == null ? null : r.getData();
        } catch (Exception e) {
            log.debug("调用 mall-order 统计失败（降级为 0）：userId={}", userId);
            return null;
        }
    }

    public StatisticsVO statistics(Long sellerId) {
        StatisticsVO vo = new StatisticsVO();
        vo.setOnSaleCount(countByStatus(sellerId, "ON_SALE"));
        vo.setDraftCount(countByStatus(sellerId, "DRAFT"));
        vo.setSoldCount(countByStatus(sellerId, "SOLD"));
        Long fav = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getSellerId, sellerId))
                .stream().map(Product::getFavoriteCount).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        vo.setFavoriteCount(fav.intValue());
        return vo;
    }

    private Integer countByStatus(Long sellerId, String status) {
        Long n = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId).eq(Product::getStatus, status));
        return n == null ? 0 : n.intValue();
    }

    /** 首页/详情共用：运费提示文案（03 §1.10、02 §5.2） */
    public static String freightHint(String mode) {
        if (mode == null) {
            return "运费见详情";
        }
        return switch (mode) {
            case "FREE_SHIPPING" -> "包邮";
            case "SELF_PICKUP" -> "仅自提（免运费）";
            default -> "同城 5 元 / 跨城 10 元";
        };
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static Integer intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDateTime timeOf(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

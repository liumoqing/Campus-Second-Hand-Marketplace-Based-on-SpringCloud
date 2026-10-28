package com.campus.mall.product.service;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.product.entity.Product;
import com.campus.mall.product.mapper.ProductMapper;
import com.campus.mall.product.vo.ProductVos.InnerProductVO;
import com.campus.mall.product.vo.ProductVos.ProductBriefVO;
import com.campus.mall.product.vo.ProductVos.StatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部接口（P27~P34）：订单服务锁定/回补/置售出，消息服务取商品卡片，用户服务取计数。
 * 全部不过网关，靠 X-Internal-Token 鉴权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InnerProductService {

    private final ProductMapper productMapper;
    private final ProductService productService;

    public InnerProductVO view(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        InnerProductVO vo = new InnerProductVO();
        vo.setId(p.getId());
        vo.setSellerId(p.getSellerId());
        vo.setTitle(p.getTitle());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setPrice(p.getPrice());
        vo.setStatus(p.getStatus());
        vo.setStock(p.getStock());
        vo.setVersion(p.getVersion());
        vo.setCity(p.getCity());
        vo.setFreightMode(p.getFreightMode());
        return vo;
    }

    public List<ProductBriefVO> batch(List<Long> ids) {
        List<ProductBriefVO> list = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return list;
        }
        for (Product p : productMapper.selectBatchIds(ids)) {
            list.add(brief(p));
        }
        return list;
    }

    public ProductBriefVO briefOf(Long id) {
        Product p = productMapper.selectById(id);
        return p == null ? null : brief(p);
    }

    private ProductBriefVO brief(Product p) {
        ProductBriefVO vo = new ProductBriefVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setPrice(p.getPrice());
        vo.setStatus(p.getStatus());
        return vo;
    }

    /**
     * P29 扣库存并置 LOCKED。三层防超卖的第二层（条件 UPDATE + version），
     * 幂等靠 lock_order_no：同一订单号重试能命中（02 §5.2）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long productId, String orderNo) {
        Product p = productMapper.selectById(productId);
        if (p == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if ("LOCKED".equals(p.getStatus()) && orderNo.equals(p.getLockOrderNo())) {
            log.info("商品已被本订单锁定，幂等返回：productId={}, orderNo={}", productId, orderNo);
            return;
        }
        if (!"ON_SALE".equals(p.getStatus())) {
            throw new BizException(ErrorCode.STOCK_LOCKED, "商品已被他人锁定或已下架");
        }
        Integer version = p.getVersion() == null ? 0 : p.getVersion();
        int rows = productMapper.lock(productId, version, orderNo);
        if (rows == 0) {
            throw new BizException(ErrorCode.STOCK_LOCKED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(Long productId, String orderNo) {
        int rows = productMapper.unlock(orderNo);
        if (rows == 0) {
            log.info("商品不需要回补（可能已回补或未锁定）：productId={}, orderNo={}", productId, orderNo);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void sold(Long productId, String orderNo) {
        int rows = productMapper.markSold(orderNo);
        if (rows == 0) {
            log.info("置售出未命中（可能已售出）：productId={}, orderNo={}", productId, orderNo);
        }
    }

    public void incrView(Long id) {
        try {
            productMapper.incrView(id);
        } catch (Exception e) {
            log.debug("浏览量自增失败：id={}", id);
        }
    }

    public StatisticsVO statistics(Long sellerId) {
        return productService.statistics(sellerId);
    }
}

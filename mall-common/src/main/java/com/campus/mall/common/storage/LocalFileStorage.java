package com.campus.mall.common.storage;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 本机本地文件存储（替代 MinIO：本机未安装 MinIO）。
 *
 * <p>约定与 03 §1.9 / P11 一致：返回可直读 URL，由 mall-product 的 /files/** 静态映射对外提供。
 * 落盘目录 {@code mall.upload.dir}（默认 %USERPROFILE%/mall-upload），
 * URL 前缀 {@code mall.upload.base-url}（默认 http://127.0.0.1:4402）。
 */
@Slf4j
@Component
public class LocalFileStorage {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path root;
    private final String baseUrl;

    public LocalFileStorage(@Value("${mall.upload.dir:${user.home}/mall-upload}") String dir,
                            @Value("${mall.upload.base-url:http://127.0.0.1:4402}") String baseUrl) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("创建上传目录失败：{}", root, e);
        }
    }

    public Path root() {
        return root;
    }

    /**
     * @param file    上传文件
     * @param bizType product / avatar / review / chat，只影响落盘子目录
     * @param maxMb   大小上限（商品 5 / 头像 2）
     * @return 可直接放进 img src 的 URL
     */
    public String save(MultipartFile file, String bizType, int maxMb) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase())) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "只允许 jpg/png/webp/gif");
        }
        if (file.getSize() > (long) maxMb * 1024 * 1024) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "文件不能超过 " + maxMb + "MB");
        }
        // 真实图片校验：能被 ImageIO 解码（03 A8 的要求）
        try (InputStream in = file.getInputStream()) {
            if (javax.imageio.ImageIO.read(in) == null) {
                throw new BizException(ErrorCode.UPLOAD_FAILED, "不是有效的图片文件");
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "图片读取失败");
        }

        String sub = (bizType == null || bizType.isBlank() ? "misc" : bizType).replaceAll("[^a-zA-Z]", "");
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String ext = switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = root.resolve(sub).resolve(month).resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "非法路径");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("写文件失败：{}", target, e);
            throw new BizException(ErrorCode.UPLOAD_FAILED);
        }
        return baseUrl + "/files/" + sub + "/" + month + "/" + name;
    }
}

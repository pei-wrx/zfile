package com.pei.zfile.storage.local;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.storage.model.StoreResult;
import com.pei.zfile.storage.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class LocalStorageService implements StorageService {

    @Value("${z-file.storage.local-path:./storage}")
    private String basePath;

    private Path baseDir;

    @PostConstruct
    public void init() {
        baseDir = Path.of(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "无法创建存储根目录: " + baseDir, e);
        }
        log.info("本地存储根目录: {}", baseDir);
    }

    @Override
    public StoreResult storeTemp(InputStream inputStream) {
        String tempKey = UUID.randomUUID().toString().replace("-", "");
        Path tempPath = baseDir.resolve("temp").resolve(tempKey);
        try {
            Files.createDirectories(tempPath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long sizeBytes;
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest);
                 OutputStream os = Files.newOutputStream(tempPath)) {
                sizeBytes = dis.transferTo(os);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            log.debug("临时文件写入完成: tempKey={}, sizeBytes={}, sha256={}", tempKey, sizeBytes, sha256);
            return new StoreResult(tempKey, sha256, sizeBytes);
        } catch (NoSuchAlgorithmException e) {
            deletePathQuietly(tempPath);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "SHA-256 算法不可用", e);
        } catch (IOException e) {
            deletePathQuietly(tempPath);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "临时文件写入失败", e);
        }
    }

    @Override
    public void commitTemp(String tempKey, String storageKey) {
        Path tempPath = resolveTempPath(tempKey);
        Path targetPath = resolveStoragePath(storageKey);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            log.debug("临时文件提交完成: tempKey={} -> storageKey={}", tempKey, storageKey);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "临时文件提交失败: " + storageKey, e);
        }
    }

    @Override
    public void deleteTemp(String tempKey) {
        Path tempPath = resolveTempPath(tempKey);
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", tempPath, e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path filePath = resolveStoragePath(storageKey);
        if (!Files.exists(filePath)) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "文件读取失败: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path filePath = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", filePath, e);
        }
    }

    @Override
    public void copy(String sourceStorageKey, String targetStorageKey) {
        Path sourcePath = resolveStoragePath(sourceStorageKey);
        Path targetPath = resolveStoragePath(targetStorageKey);
        try {
            Files.copy(sourcePath, targetPath);
        } catch (IOException e) {
            deletePathQuietly(targetPath);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "复制物理文件失败", e);
        }
    }

    private Path resolveStoragePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "存储键不能为空");
        }
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir) || resolved.startsWith(baseDir.resolve("temp"))) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "非法存储键");
        }
        return resolved;
    }

    private Path resolveTempPath(String tempKey) {
        if (tempKey == null || tempKey.isBlank()) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "临时存储键不能为空");
        }
        Path tempDir = baseDir.resolve("temp");
        Path resolved = tempDir.resolve(tempKey).normalize();
        if (!resolved.startsWith(tempDir)) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "非法临时存储键");
        }
        return resolved;
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupError) {
            log.warn("清理文件失败: {}", path, cleanupError);
        }
    }
}

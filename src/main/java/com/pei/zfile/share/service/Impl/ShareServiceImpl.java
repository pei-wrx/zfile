package com.pei.zfile.share.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.share.dto.CreateShareRequest;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.dto.ShareResponse;
import com.pei.zfile.share.dto.ShareVerifyRequest;
import com.pei.zfile.share.dto.ShareVerifyResponse;
import com.pei.zfile.share.entity.Share;
import com.pei.zfile.share.entity.ShareItem;
import com.pei.zfile.share.mapper.ShareItemMapper;
import com.pei.zfile.share.mapper.ShareMapper;
import com.pei.zfile.share.service.ShareService;
import com.pei.zfile.common.security.JwtTokenProvider;
import com.pei.zfile.common.security.JwtTokenProvider.TokenValidationResult;
import com.pei.zfile.common.security.RedisRequestRateLimiter;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.pei.zfile.common.util.RedisConstant.SHARE_TOKEN_KEY;
import static com.pei.zfile.common.util.RedisConstant.SHARE_TOKEN_TTL;

@Service
public class ShareServiceImpl implements ShareService {

    private static final String SHARE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHARE_CODE_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private ShareMapper shareMapper;

    @Autowired
    private ShareItemMapper shareItemMapper;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private StorageService storageService;

    @Autowired
    private RedisRequestRateLimiter rateLimiter;

    @Value("${z-file.share.base-url:http://localhost:8090}")
    private String shareBaseUrl;

    @Override
    @Transactional
    public ShareResponse create(Long userId, CreateShareRequest request) {
        if (new HashSet<>(request.getNodeIds()).size() != request.getNodeIds().size()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "分享节点不能重复");
        }
        List<FileNode> nodes = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .in(FileNode::getId, request.getNodeIds())
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (nodes.size() != request.getNodeIds().size()) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        Share share = new Share()
                .setOwnerId(userId)
                .setShareCode(generateShareCode())
                .setTitle(request.getTitle())
                .setPasswordHash(request.getPassword() != null
                        ? passwordEncoder.encode(request.getPassword()) : null)
                .setExpiresAt(request.getExpiresAt())
                .setDownloadLimit(request.getDownloadLimit())
                .setDownloadCount(0)
                .setStatus("ACTIVE")
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        shareMapper.insert(share);

        for (Long nodeId : request.getNodeIds()) {
            shareItemMapper.insert(new ShareItem()
                    .setShareId(share.getId())
                    .setNodeId(nodeId));
        }

        return toShareResponse(share, request.getNodeIds());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ShareResponse> list(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<Share> wrapper = new LambdaQueryWrapper<Share>()
                .eq(Share::getOwnerId, userId)
                .eq(status != null && !status.isBlank(), Share::getStatus, status)
                .orderByDesc(Share::getCreatedAt);

        Page<Share> sharePage = new Page<>(page, size);
        Page<Share> result = shareMapper.selectPage(sharePage, wrapper);

        List<Share> shares = result.getRecords();
        List<Long> shareIds = shares.stream().map(Share::getId).toList();

        Map<Long, List<Long>> nodeIdsMap;
        if (!shareIds.isEmpty()) {
            List<ShareItem> shareItems = shareItemMapper.selectList(
                    new LambdaQueryWrapper<ShareItem>()
                            .in(ShareItem::getShareId, shareIds));
            nodeIdsMap = shareItems.stream()
                    .collect(Collectors.groupingBy(ShareItem::getShareId,
                            Collectors.mapping(ShareItem::getNodeId, Collectors.toList())));
        } else {
            nodeIdsMap = new HashMap<>();
        }

        List<ShareResponse> items = shares.stream()
                .map(share -> toShareResponse(share,
                        nodeIdsMap.getOrDefault(share.getId(), List.of())))
                .toList();

        return PageResult.of(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public ShareResponse detail(Long userId, Long shareId) {
        Share share = shareMapper.selectById(shareId);
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!share.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }

        List<Long> nodeIds = shareItemMapper.selectList(
                new LambdaQueryWrapper<ShareItem>()
                        .eq(ShareItem::getShareId, shareId))
                .stream()
                .map(ShareItem::getNodeId)
                .toList();

        return toShareResponse(share, nodeIds);
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long shareId) {
        Share share = shareMapper.selectById(shareId);
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!share.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (share.getStatus().equals("CANCELLED")) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        share.setStatus("CANCELLED");
        share.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        shareMapper.updateById(share);
    }

    @Override
    public PublicShareResponse getPublicShareDetail(String shareCode) {
        Share share = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>()
                        .eq(Share::getShareCode, shareCode)
        );
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(share.getStatus())) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        if (isExpired(share)) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        return toPublicShareResponse(share);
    }

    @Override
    public ShareVerifyResponse verifySharePassword(String shareCode, ShareVerifyRequest request, String clientIp) {
        Share share = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>()
                        .eq(Share::getShareCode, shareCode)
        );
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(share.getStatus())) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        if (isExpired(share)) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        if (share.getPasswordHash() != null) {
            String rateLimitKey = rateLimiter.check(
                    "share-password", clientIp + "|" + shareCode.toLowerCase(Locale.ROOT),
                    5, Duration.ofMinutes(5));
            if (request.getPassword() == null
                    || !passwordEncoder.matches(request.getPassword(), share.getPasswordHash())) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_INVALID);
            }
            rateLimiter.reset(rateLimitKey);
        }
        String shareToken = jwtTokenProvider.generateShareToken(shareCode);
        String tokenId = jwtTokenProvider.parseClaims(shareToken).getId();
        stringRedisTemplate.opsForValue()
                .set(SHARE_TOKEN_KEY + tokenId, shareCode, SHARE_TOKEN_TTL, TimeUnit.SECONDS);
        return ShareVerifyResponse.builder()
                .shareToken(shareToken)
                .expiresIn(SHARE_TOKEN_TTL)
                .build();
    }

    @Override
    public List<NodeResponse> browseShareNodes(String shareCode, Long parentId, String shareToken) {
        Share share = validateShareAccess(shareCode, shareToken);

        if (parentId == null) {
            List<Long> nodeIds = shareItemMapper.selectList(
                    new LambdaQueryWrapper<ShareItem>()
                            .eq(ShareItem::getShareId, share.getId()))
                    .stream()
                    .map(ShareItem::getNodeId)
                    .toList();
            if (nodeIds.isEmpty()) {
                return List.of();
            }
            return fileNodeMapper.selectList(
                    new LambdaQueryWrapper<FileNode>()
                            .in(FileNode::getId, nodeIds)
                            .eq(FileNode::getOwnerId, share.getOwnerId())
                            .eq(FileNode::getStatus, "ACTIVE"))
                    .stream()
                    .map(this::toNodeResponse)
                    .toList();
        }

        FileNode parent = getActiveOwnedNode(parentId, share.getOwnerId());
        if (!"FOLDER".equals(parent.getNodeType()) || !isNodeWithinShare(share, parent)) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        return fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getParentId, parentId)
                        .eq(FileNode::getOwnerId, share.getOwnerId())
                        .eq(FileNode::getStatus, "ACTIVE"))
                .stream()
                .map(this::toNodeResponse)
                .toList();
    }

    @Override
    public FileResource downloadShareFile(String shareCode, Long fileId, String shareToken) {
        Share share = validateShareAccess(shareCode, shareToken);

        FileNode fileNode = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, fileId)
                        .eq(FileNode::getOwnerId, share.getOwnerId())
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (fileNode == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        if (!"FILE".equals(fileNode.getNodeType())) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "该节点不是文件");
        }
        if (!isNodeWithinShare(share, fileNode)) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        InputStream inputStream = storageService.load(fileNode.getStorageKey());

        if (share.getDownloadLimit() != null) {
            boolean updated = shareMapper.update(null,
                    new LambdaUpdateWrapper<Share>()
                            .eq(Share::getId, share.getId())
                            .eq(Share::getStatus, "ACTIVE")
                            .lt(Share::getDownloadCount, share.getDownloadLimit())
                            .setSql("download_count = download_count + 1")
            ) > 0;
            if (!updated) {
                throw new BusinessException(ResultCode.DOWNLOAD_LIMIT_REACHED);
            }
        } else {
            shareMapper.update(null,
                    new LambdaUpdateWrapper<Share>()
                            .eq(Share::getId, share.getId())
                            .eq(Share::getStatus, "ACTIVE")
                            .setSql("download_count = download_count + 1")
            );
        }

        return new FileResource(inputStream, fileNode.getContentType(), fileNode.getName(), fileNode.getSizeBytes());
    }

    private Share validateShareAccess(String shareCode, String shareToken) {
        Share share = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>()
                        .eq(Share::getShareCode, shareCode)
        );
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(share.getStatus())) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        if (isExpired(share)) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }

        if (share.getPasswordHash() != null) {
            if (shareToken == null || shareToken.isBlank()) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_REQUIRED);
            }
            TokenValidationResult validationResult = jwtTokenProvider.validateToken(shareToken);
            if (validationResult != TokenValidationResult.VALID) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_INVALID);
            }
            var claims = jwtTokenProvider.parseClaims(shareToken);
            if (!jwtTokenProvider.hasTokenType(claims, JwtTokenProvider.SHARE_TOKEN_TYPE)) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_INVALID);
            }
            String tokenSubject = claims.getSubject();
            if (!shareCode.equals(tokenSubject)) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_INVALID);
            }
            String storedShareCode = stringRedisTemplate.opsForValue().get(SHARE_TOKEN_KEY + claims.getId());
            if (!shareCode.equals(storedShareCode)) {
                throw new BusinessException(ResultCode.SHARE_PASSWORD_INVALID);
            }
        }
        return share;
    }

    private boolean isExpired(Share share) {
        return share.getExpiresAt() != null
                && share.getExpiresAt().isBefore(Instant.now());
    }

    private FileNode getActiveOwnedNode(Long nodeId, Long ownerId) {
        FileNode node = fileNodeMapper.selectById(nodeId);
        if (node == null || !ownerId.equals(node.getOwnerId()) || !"ACTIVE".equals(node.getStatus())) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        return node;
    }

    private boolean isNodeWithinShare(Share share, FileNode node) {
        Set<Long> sharedRootIds = new HashSet<>(shareItemMapper.selectList(
                        new LambdaQueryWrapper<ShareItem>()
                                .eq(ShareItem::getShareId, share.getId()))
                .stream()
                .map(ShareItem::getNodeId)
                .toList());
        if (sharedRootIds.contains(node.getId())) {
            return true;
        }

        Set<Long> visited = new HashSet<>();
        Long parentId = node.getParentId();
        while (parentId != null && visited.add(parentId)) {
            if (sharedRootIds.contains(parentId)) {
                FileNode sharedRoot = fileNodeMapper.selectById(parentId);
                return sharedRoot != null
                        && share.getOwnerId().equals(sharedRoot.getOwnerId())
                        && "ACTIVE".equals(sharedRoot.getStatus())
                        && "FOLDER".equals(sharedRoot.getNodeType());
            }
            FileNode parent = fileNodeMapper.selectById(parentId);
            if (parent == null
                    || !share.getOwnerId().equals(parent.getOwnerId())
                    || !"ACTIVE".equals(parent.getStatus())) {
                return false;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    private NodeResponse toNodeResponse(FileNode node) {
        return NodeResponse.builder()
                .id(node.getId())
                .parentId(node.getParentId())
                .type(node.getNodeType())
                .name(node.getName())
                .sizeBytes(node.getSizeBytes())
                .contentType(node.getContentType())
                .status(node.getStatus())
                .deletedAt(node.getDeletedAt())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    private PublicShareResponse toPublicShareResponse(Share share) {
        return PublicShareResponse.builder()
                .shareCode(share.getShareCode())
                .shareUrl(buildShareUrl(share.getShareCode()))
                .title(share.getTitle())
                .hasPassword(share.getPasswordHash() != null)
                .expiresAt(share.getExpiresAt())
                .downloadLimit(share.getDownloadLimit())
                .downloadCount(share.getDownloadCount())
                .status(share.getStatus())
                .createdAt(share.getCreatedAt())
                .build();
    }

    private String buildShareUrl(String shareCode) {
        if (shareBaseUrl.endsWith("/")) {
            return shareBaseUrl + "s/" + shareCode;
        }
        return shareBaseUrl + "/s/" + shareCode;
    }

    private ShareResponse toShareResponse(Share share, List<Long> nodeIds) {
        return ShareResponse.builder()
                .id(share.getId())
                .shareCode(share.getShareCode())
                .shareUrl(buildShareUrl(share.getShareCode()))
                .title(share.getTitle())
                .hasPassword(share.getPasswordHash() != null)
                .expiresAt(share.getExpiresAt())
                .downloadLimit(share.getDownloadLimit())
                .downloadCount(share.getDownloadCount())
                .status(share.getStatus())
                .nodeIds(nodeIds)
                .createdAt(share.getCreatedAt())
                .build();
    }

    private String generateShareCode() {
        StringBuilder sb = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            sb.append(SHARE_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(SHARE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

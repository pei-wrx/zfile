package com.pei.zfile.file.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.*;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.service.NodeService;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;

@Service
public class NodeServiceImpl implements NodeService {

    @Resource
    private FileNodeMapper fileNodeMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StorageService storageService;

    @Override
    @Transactional
    public NodeResponse createFolder(Long userId, CreateFolderRequest request) {
        validateParentFolder(request.getParentId(), userId);

        boolean exists = fileNodeMapper.exists(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(request.getParentId() != null, FileNode::getParentId, request.getParentId())
                        .isNull(request.getParentId() == null, FileNode::getParentId)
                        .eq(FileNode::getName, request.getName())
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (exists) {
            throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
        }

        FileNode folder = new FileNode()
                .setOwnerId(userId)
                .setParentId(request.getParentId())
                .setNodeType("FOLDER")
                .setName(request.getName())
                .setSizeBytes(0L)
                .setStatus("ACTIVE");

        fileNodeMapper.insert(folder);

        return toNodeResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NodeResponse> listNodes(Long userId, ListNodesRequest request) {
        boolean isSearch = request.getKeyword() != null && !request.getKeyword().isBlank();

        if (!isSearch) {
            validateParentFolder(request.getParentId(), userId);
        }

        LambdaQueryWrapper<FileNode> wrapper = new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getOwnerId, userId)
                .eq(FileNode::getStatus, "ACTIVE");

        if (isSearch) {
            wrapper.like(FileNode::getName, request.getKeyword());
        } else {
            if (request.getParentId() != null) {
                wrapper.eq(FileNode::getParentId, request.getParentId());
            } else {
                wrapper.isNull(true, FileNode::getParentId);
            }
        }

        if (request.getType() != null && !request.getType().isBlank()) {
            wrapper.eq(FileNode::getNodeType, request.getType());
        }

        applyOrder(wrapper, request);

        int pageNum = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getSize() != null ? request.getSize() : 20;
        Page<FileNode> page = new Page<>(pageNum, pageSize);

        Page<FileNode> result = fileNodeMapper.selectPage(page, wrapper);

        List<NodeResponse> items = result.getRecords().stream()
                .map(this::toNodeResponse)
                .toList();

        return PageResult.of(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Override
    public NodeDetailResponse getNodeDetails(Long userId, Long nodeId) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        List<BreadcrumbItem> breadcrumbs = buildBreadcrumbs(node.getParentId(), userId);

        return NodeDetailResponse.builder()
                .id(node.getId())
                .parentId(node.getParentId())
                .type(node.getNodeType())
                .name(node.getName())
                .sizeBytes(node.getSizeBytes())
                .contentType(node.getContentType())
                .checksum(node.getChecksum())
                .status(node.getStatus())
                .deletedAt(node.getDeletedAt())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .breadcrumbs(breadcrumbs)
                .build();
    }

    @Override
    @Transactional
    public NodeResponse renameNode(Long userId, Long nodeId, String name) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        boolean exists = fileNodeMapper.exists(
                matchParent(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
                        .ne(FileNode::getId, nodeId), node.getParentId())
        );
        if (exists) {
            throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
        }
        node.setName(name);
        fileNodeMapper.updateById(node);
        return toNodeResponse(node);
    }

    private List<BreadcrumbItem> buildBreadcrumbs(Long parentId, Long userId) {
        List<BreadcrumbItem> breadcrumbs = new ArrayList<>();
        Long currentParentId = parentId;
        while (currentParentId != null) {
            FileNode parent = fileNodeMapper.selectOne(
                    new LambdaQueryWrapper<FileNode>()
                            .eq(FileNode::getId, currentParentId)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "ACTIVE")
            );
            if (parent == null) {
                break;
            }
            breadcrumbs.addFirst(BreadcrumbItem.builder()
                    .id(parent.getId())
                    .name(parent.getName())
                    .build());
            currentParentId = parent.getParentId();
        }
        return breadcrumbs;
    }

    private void validateParentFolder(Long parentId, Long userId) {
        if (parentId == null) {
            return;
        }
        FileNode parent = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, parentId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (parent == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        if (!"FOLDER".equals(parent.getNodeType())) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "父节点必须是目录");
        }
    }

    private void applyOrder(LambdaQueryWrapper<FileNode> wrapper, ListNodesRequest request) {
        String sort = request.getSort() != null ? request.getSort() : "name";
        boolean asc = !"desc".equalsIgnoreCase(request.getDirection());

        switch (sort) {
            case "size" -> wrapper.orderBy(true, asc, FileNode::getSizeBytes);
            case "createdAt" -> wrapper.orderBy(true, asc, FileNode::getCreatedAt);
            case "updatedAt" -> wrapper.orderBy(true, asc, FileNode::getUpdatedAt);
            default -> wrapper.orderBy(true, asc, FileNode::getName);
        }
    }

    @Override
    @Transactional
    public void trashNode(Long userId, Long nodeId) {
        doTrashNode(userId, nodeId);
    }

    @Override
    @Transactional
    public void batchTrashNode(Long userId, List<Long> nodeIds) {
        if (new HashSet<>(nodeIds).size() != nodeIds.size()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "节点不能重复");
        }
        for (Long nodeId : nodeIds) {
            doTrashNode(userId, nodeId);
        }
    }

    @Override
    @Transactional
    public NodeResponse moveNode(Long userId, Long nodeId, MoveNodeRequest request) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        Long targetParentId = request.getTargetParentId();
        validateAndCheckTargetParent(targetParentId, nodeId, node.getNodeType(), userId);

        String name = node.getName();
        boolean nameConflict = fileNodeMapper.exists(
                matchParent(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
                        .ne(FileNode::getId, nodeId), targetParentId)
        );
        if (nameConflict) {
            if ("RENAME".equals(request.getConflictPolicy())) {
                name = generateAutoRenameName(targetParentId, userId, name);
            } else {
                throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
            }
        }

        node.setParentId(targetParentId);
        node.setName(name);
        fileNodeMapper.updateById(node);
        return toNodeResponse(node);
    }

    @Override
    @Transactional
    public List<NodeResponse> copyNodes(Long userId, CopyNodesRequest request) {
        if (new HashSet<>(request.getNodeIds()).size() != request.getNodeIds().size()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "复制节点不能重复");
        }
        Long targetParentId = request.getTargetParentId();
        validateAndCheckTargetParent(targetParentId, null, null, userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户已被禁用");
        }

        List<FileNode> sourceNodes = new ArrayList<>();
        long totalBytes = 0L;
        for (Long nodeId : request.getNodeIds()) {
            FileNode sourceNode = getActiveOwnedNode(nodeId, userId);
            if ("FOLDER".equals(sourceNode.getNodeType())
                    && targetParentId != null
                    && (targetParentId.equals(nodeId) || isDescendantOf(targetParentId, nodeId, userId))) {
                throw new BusinessException(ResultCode.INVALID_OPERATION, "不能将文件夹复制到其子目录中");
            }
            sourceNodes.add(sourceNode);
            totalBytes = safeAdd(totalBytes, calculateSubtreeSize(sourceNode, userId));
        }

        long newUsedBytes = safeAdd(user.getUsedBytes(), totalBytes);
        if (newUsedBytes > user.getQuotaBytes()) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED);
        }

        List<NodeResponse> copiedNodes = new ArrayList<>();
        List<String> copiedStorageKeys = new ArrayList<>();
        registerRollbackCleanup(copiedStorageKeys);
        for (FileNode sourceNode : sourceNodes) {
            String newName = resolveNameConflict(targetParentId, userId, sourceNode.getName(), request.getConflictPolicy());
            FileNode copiedNode = deepCopyNode(
                    sourceNode, targetParentId, userId, newName, request.getConflictPolicy(), copiedStorageKeys);
            copiedNodes.add(toNodeResponse(copiedNode));
        }

        int updated = userMapper.updateById(new User()
                .setId(userId)
                .setUsedBytes(newUsedBytes)
                .setVersion(user.getVersion()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "存储用量已变化，请重试");
        }
        return copiedNodes;
    }

    private FileNode getActiveOwnedNode(Long nodeId, Long userId) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE"));
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        return node;
    }

    private long calculateSubtreeSize(FileNode node, Long userId) {
        if ("FILE".equals(node.getNodeType())) {
            return node.getSizeBytes() != null ? node.getSizeBytes() : 0L;
        }
        long total = 0L;
        List<FileNode> children = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getParentId, node.getId())
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE"));
        for (FileNode child : children) {
            total = safeAdd(total, calculateSubtreeSize(child, userId));
        }
        return total;
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED);
        }
    }

    private void doTrashNode(Long userId, Long nodeId) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        markNodeAsTrashed(node, node.getParentId(), now);

        if ("FOLDER".equals(node.getNodeType())) {
            List<Long> descendantIds = collectAllDescendantIds(nodeId, userId);
            if (!descendantIds.isEmpty()) {
                fileNodeMapper.update(null,
                        new LambdaUpdateWrapper<FileNode>()
                                .in(FileNode::getId, descendantIds)
                                .eq(FileNode::getOwnerId, userId)
                                .eq(FileNode::getStatus, "ACTIVE")
                                .set(FileNode::getStatus, "TRASHED")
                                .set(FileNode::getDeletedAt, now)
                );
            }
        }
    }

    private void markNodeAsTrashed(FileNode node, Long originalParentId, LocalDateTime deletedAt) {
        node.setStatus("TRASHED");
        node.setOriginalParentId(originalParentId);
        node.setDeletedAt(deletedAt);
        fileNodeMapper.updateById(node);
    }

    private List<Long> collectAllDescendantIds(Long folderId, Long userId) {
        List<Long> allDescendants = new ArrayList<>();
        List<Long> currentLevel = List.of(folderId);

        while (!currentLevel.isEmpty()) {
            List<FileNode> children = fileNodeMapper.selectList(
                    new LambdaQueryWrapper<FileNode>()
                            .in(FileNode::getParentId, currentLevel)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "ACTIVE")
            );
            currentLevel = children.stream()
                    .peek(child -> allDescendants.add(child.getId()))
                    .filter(child -> "FOLDER".equals(child.getNodeType()))
                    .map(FileNode::getId)
                    .toList();
        }
        return allDescendants;
    }

    private boolean isDescendantOf(Long ancestorId, Long nodeId, Long userId) {
        List<Long> descendantIds = collectAllDescendantIds(nodeId, userId);
        return descendantIds.contains(ancestorId);
    }

    private void validateAndCheckTargetParent(Long targetParentId, Long sourceNodeId, String sourceNodeType, Long userId) {
        if (targetParentId == null) {
            return;
        }
        if (targetParentId.equals(sourceNodeId)) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "不能将节点移动到自身");
        }
        if ("FOLDER".equals(sourceNodeType) && isDescendantOf(targetParentId, sourceNodeId, userId)) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "不能将文件夹移动到其子目录中");
        }
        FileNode targetParent = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, targetParentId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (targetParent == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        if (!"FOLDER".equals(targetParent.getNodeType())) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "目标父节点必须是目录");
        }
    }

    private String resolveNameConflict(Long parentId, Long userId, String name, String conflictPolicy) {
        boolean exists = fileNodeMapper.exists(
                matchParent(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE"), parentId)
        );
        if (!exists) {
            return name;
        }
        if ("RENAME".equals(conflictPolicy)) {
            return generateAutoRenameName(parentId, userId, name);
        }
        throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
    }

    private FileNode deepCopyNode(FileNode source, Long targetParentId, Long userId, String name,
                                  String conflictPolicy, List<String> copiedStorageKeys) {
        String storageKey = null;
        if ("FILE".equals(source.getNodeType())) {
            storageKey = UUID.randomUUID().toString().replace("-", "");
            storageService.copy(source.getStorageKey(), storageKey);
            copiedStorageKeys.add(storageKey);
        }
        FileNode copy = new FileNode()
                .setOwnerId(userId)
                .setParentId(targetParentId)
                .setNodeType(source.getNodeType())
                .setName(name)
                .setSizeBytes(source.getSizeBytes())
                .setContentType(source.getContentType())
                .setChecksum(source.getChecksum())
                .setStatus("ACTIVE")
                .setStorageKey(storageKey);
        fileNodeMapper.insert(copy);

        if ("FOLDER".equals(source.getNodeType())) {
            List<FileNode> children = fileNodeMapper.selectList(
                    new LambdaQueryWrapper<FileNode>()
                            .eq(FileNode::getParentId, source.getId())
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "ACTIVE")
            );
            for (FileNode child : children) {
                String childName = resolveNameConflict(copy.getId(), userId, child.getName(), conflictPolicy);
                deepCopyNode(child, copy.getId(), userId, childName, conflictPolicy, copiedStorageKeys);
            }
        }
        return copy;
    }

    private String generateAutoRenameName(Long parentId, Long userId, String originalName) {
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        String newName;
        do {
            newName = baseName + " (" + counter + ")" + extension;
            counter++;
        } while (fileNodeMapper.exists(
                matchParent(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getName, newName)
                        .eq(FileNode::getStatus, "ACTIVE"), parentId)
        ));

        return newName;
    }

    private LambdaQueryWrapper<FileNode> matchParent(LambdaQueryWrapper<FileNode> wrapper, Long parentId) {
        return parentId == null
                ? wrapper.isNull(FileNode::getParentId)
                : wrapper.eq(FileNode::getParentId, parentId);
    }

    private void registerRollbackCleanup(List<String> storageKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageKeys.forEach(storageService::delete);
                }
            }
        });
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
}

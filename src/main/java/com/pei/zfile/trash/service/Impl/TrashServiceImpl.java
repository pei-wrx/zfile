package com.pei.zfile.trash.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.trash.dto.RestoreNodeRequest;
import com.pei.zfile.trash.dto.TrashNodesRequest;
import com.pei.zfile.trash.service.TrashService;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrashServiceImpl implements TrashService {

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @Autowired
    private StorageService storageService;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<NodeResponse> selectRootNode(Long userId, TrashNodesRequest request) {

        int pageNum = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getSize() != null ? request.getSize() : 20;

        Page<FileNode> page = new Page<>(pageNum, pageSize);

        List<Long> trashedNodeIds = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .select(FileNode::getId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "TRASHED")
        ).stream().map(FileNode::getId).collect(Collectors.toList());

        LambdaQueryWrapper<FileNode> wrapper = new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getOwnerId, userId)
                .eq(FileNode::getStatus, "TRASHED")
                .orderByDesc(FileNode::getDeletedAt);

        wrapper.and(w -> w.isNull(FileNode::getParentId)
                .or(w2 -> w2.notIn(!trashedNodeIds.isEmpty(), FileNode::getParentId, trashedNodeIds)));

        Page<FileNode> result = fileNodeMapper.selectPage(page, wrapper);
        List<NodeResponse> items = result.getRecords().stream()
                .map(this::toNodeResponse)
                .toList();

        return PageResult.of(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Override
    @Transactional
    public void deleteRootNodes(Long userId) {
        List<FileNode> trashedNodes = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "TRASHED")
        );

        long releasedBytes = calculateReleasedBytes(trashedNodes);

        if (!trashedNodes.isEmpty()) {
            fileNodeMapper.delete(
                    new LambdaQueryWrapper<FileNode>()
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "TRASHED")
            );
        }

        deductQuota(userId, releasedBytes);
        deletePhysicalFilesAfterCommit(trashedNodes);
    }

    @Override
    @Transactional
    public NodeResponse restoreNode(Long userId, Long nodeId, RestoreNodeRequest request) {
        if (request == null) {
            request = new RestoreNodeRequest();
        }
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "TRASHED")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        Long targetParentId = request.getTargetParentId() != null
                ? request.getTargetParentId()
                : node.getOriginalParentId();

        if (targetParentId != null) {
            FileNode targetParent = fileNodeMapper.selectOne(
                    new LambdaQueryWrapper<FileNode>()
                            .eq(FileNode::getId, targetParentId)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "ACTIVE")
            );
            if (targetParent == null) {
                targetParentId = null;
            } else if (!"FOLDER".equals(targetParent.getNodeType())) {
                throw new BusinessException(ResultCode.INVALID_OPERATION, "目标父节点必须是目录");
            }
        }

        String conflictPolicy = request.getConflictPolicy() != null
                ? request.getConflictPolicy()
                : "REJECT";

        String name = resolveRestoreNameConflict(targetParentId, userId, node.getName(), nodeId, conflictPolicy);

        node.setName(name);
        node.setStatus("ACTIVE");
        node.setParentId(targetParentId);
        node.setOriginalParentId(null);
        node.setDeletedAt(null);
        fileNodeMapper.updateById(node);

        if ("FOLDER".equals(node.getNodeType())) {
            restoreDescendants(nodeId, userId);
        }
        return toNodeResponse(node);
    }

    @Override
    @Transactional
    public void deleteNode(Long userId, Long nodeId) {
        FileNode node = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, nodeId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "TRASHED")
        );
        if (node == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        List<FileNode> allNodes = new ArrayList<>();
        allNodes.add(node);
        if ("FOLDER".equals(node.getNodeType())) {
            allNodes.addAll(collectTrashedDescendants(nodeId, userId));
        }

        long releasedBytes = calculateReleasedBytes(allNodes);

        fileNodeMapper.delete(
                new LambdaQueryWrapper<FileNode>()
                        .in(FileNode::getId, allNodes.stream().map(FileNode::getId).toList())
        );
        deductQuota(userId, releasedBytes);
        deletePhysicalFilesAfterCommit(allNodes);
    }

    private void restoreDescendants(Long folderId, Long userId) {
        List<FileNode> descendants = collectTrashedDescendants(folderId, userId);
        if (!descendants.isEmpty()) {
            List<Long> descendantIds = descendants.stream().map(FileNode::getId).toList();
            fileNodeMapper.update(null,
                    new LambdaUpdateWrapper<FileNode>()
                            .in(FileNode::getId, descendantIds)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "TRASHED")
                            .set(FileNode::getStatus, "ACTIVE")
                            .set(FileNode::getDeletedAt, null)
            );
        }
    }

    private List<FileNode> collectTrashedDescendants(Long folderId, Long userId) {
        List<FileNode> allDescendants = new ArrayList<>();
        List<Long> currentLevel = List.of(folderId);

        while (!currentLevel.isEmpty()) {
            List<FileNode> children = fileNodeMapper.selectList(
                    new LambdaQueryWrapper<FileNode>()
                            .in(FileNode::getParentId, currentLevel)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "TRASHED")
            );
            currentLevel = children.stream()
                    .peek(allDescendants::add)
                    .filter(child -> "FOLDER".equals(child.getNodeType()))
                    .map(FileNode::getId)
                    .toList();
        }
        return allDescendants;
    }

    private long calculateReleasedBytes(List<FileNode> nodes) {
        long releasedBytes = 0L;
        for (FileNode node : nodes) {
            if ("FILE".equals(node.getNodeType()) && node.getStorageKey() != null) {
                releasedBytes += node.getSizeBytes() != null ? node.getSizeBytes() : 0L;
            }
        }
        return releasedBytes;
    }

    private void deductQuota(Long userId, long releasedBytes) {
        if (releasedBytes > 0) {
            int updated = userMapper.update(null,
                    new LambdaUpdateWrapper<User>()
                            .eq(User::getId, userId)
                            .setSql("used_bytes = GREATEST(used_bytes - " + releasedBytes + ", 0)"));
            if (updated == 0) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
        }
    }

    private String resolveRestoreNameConflict(Long parentId, Long userId, String name, Long excludeId, String conflictPolicy) {
        boolean nameConflict = fileNodeMapper.exists(
                matchParent(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
                        .ne(FileNode::getId, excludeId), parentId)
        );
        if (!nameConflict) {
            return name;
        }
        if ("RENAME".equals(conflictPolicy)) {
            return generateAutoRenameName(parentId, userId, name);
        }
        throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
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

    private void deletePhysicalFilesAfterCommit(List<FileNode> nodes) {
        List<String> storageKeys = nodes.stream()
                .filter(node -> "FILE".equals(node.getNodeType()) && node.getStorageKey() != null)
                .map(FileNode::getStorageKey)
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageKeys.forEach(storageService::delete);
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

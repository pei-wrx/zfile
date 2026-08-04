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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NodeServiceImpl implements NodeService {

    @Resource
    private FileNodeMapper fileNodeMapper;

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
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getParentId, node.getParentId())
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
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
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getParentId, targetParentId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
                        .ne(FileNode::getId, nodeId)
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
        Long targetParentId = request.getTargetParentId();
        validateAndCheckTargetParent(targetParentId, null, null, userId);

        List<NodeResponse> copiedNodes = new ArrayList<>();
        for (Long nodeId : request.getNodeIds()) {
            FileNode sourceNode = fileNodeMapper.selectOne(
                    new LambdaQueryWrapper<FileNode>()
                            .eq(FileNode::getId, nodeId)
                            .eq(FileNode::getOwnerId, userId)
                            .eq(FileNode::getStatus, "ACTIVE")
            );
            if (sourceNode == null) {
                throw new BusinessException(ResultCode.FILE_NOT_FOUND);
            }

            if ("FOLDER".equals(sourceNode.getNodeType())
                    && targetParentId != null
                    && isDescendantOf(targetParentId, nodeId, userId)) {
                throw new BusinessException(ResultCode.INVALID_OPERATION, "不能将文件夹复制到其子目录中");
            }

            String newName = resolveNameConflict(targetParentId, userId, sourceNode.getName(), request.getConflictPolicy());
            FileNode copiedNode = deepCopyNode(sourceNode, targetParentId, userId, newName, request.getConflictPolicy());
            copiedNodes.add(toNodeResponse(copiedNode));
        }
        return copiedNodes;
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

        LocalDateTime now = LocalDateTime.now();
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
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getParentId, parentId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (!exists) {
            return name;
        }
        if ("RENAME".equals(conflictPolicy)) {
            return generateAutoRenameName(parentId, userId, name);
        }
        throw new BusinessException(ResultCode.FILE_NAME_CONFLICT);
    }

    private FileNode deepCopyNode(FileNode source, Long targetParentId, Long userId, String name, String conflictPolicy) {
        FileNode copy = new FileNode()
                .setOwnerId(userId)
                .setParentId(targetParentId)
                .setNodeType(source.getNodeType())
                .setName(name)
                .setSizeBytes(source.getSizeBytes())
                .setContentType(source.getContentType())
                .setChecksum(source.getChecksum())
                .setStatus("ACTIVE");
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
                deepCopyNode(child, copy.getId(), userId, childName, conflictPolicy);
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
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getParentId, parentId)
                        .eq(FileNode::getName, newName)
                        .eq(FileNode::getStatus, "ACTIVE")
        ));

        return newName;
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
package com.pei.zfile.file.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.ConflictPolicyEnum;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.dto.UploadFileRequest;
import com.pei.zfile.file.dto.UploadCheckRequest;
import com.pei.zfile.file.dto.UploadCheckResponse;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.service.FileService;
import com.pei.zfile.file.service.FileObjectService;
import com.pei.zfile.storage.model.StoreResult;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private static final long MAX_FILE_SIZE = 120L * 1024 * 1024;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StorageService storageService;

    @Autowired
    private FileObjectService fileObjectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeResponse uploadFile(Long userId, UploadFileRequest request) {
        MultipartFile file = request.getFile();
        String originalFilename = file.getOriginalFilename();

        User user = validateAndGetUser(userId);
        validateFileName(originalFilename);
        validateParentFolder(request.getParentId(), userId);

        ConflictPolicyEnum policy = ConflictPolicyEnum.fromValue(request.getConflictPolicy());
        if (policy == ConflictPolicyEnum.REPLACE) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "V1 暂不支持 REPLACE 策略，请使用 REJECT 或 RENAME");
        }
        String resolvedName = resolveNameConflict(request.getParentId(), userId, originalFilename, policy);

        StoreResult storeResult;
        try {
            storeResult = storageService.storeTemp(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "读取上传文件失败", e);
        }

        if (storeResult.getSizeBytes() > MAX_FILE_SIZE) {
            storageService.deleteTemp(storeResult.getTempKey());
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }

        if (user.getUsedBytes() + storeResult.getSizeBytes() > user.getQuotaBytes()) {
            storageService.deleteTemp(storeResult.getTempKey());
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED);
        }

        if (request.getSha256() != null
                && !request.getSha256().equalsIgnoreCase(storeResult.getSha256())) {
            storageService.deleteTemp(storeResult.getTempKey());
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "文件 SHA-256 校验失败");
        }

        FileObject fileObject = fileObjectService.createOrRetain(storeResult, file.getContentType());

        FileNode fileNode = new FileNode()
                .setOwnerId(userId)
                .setParentId(request.getParentId())
                .setNodeType("FILE")
                .setName(resolvedName)
                .setSizeBytes(fileObject.getSizeBytes())
                .setFileObjectId(fileObject.getId())
                .setContentType(fileObject.getContentType())
                .setStorageKey(fileObject.getStorageKey())
                .setChecksum(fileObject.getChecksum())
                .setStatus("ACTIVE");

        fileNodeMapper.insert(fileNode);
        int updated = userMapper.updateById(
                new User()
                        .setId(userId)
                        .setUsedBytes(user.getUsedBytes() + fileObject.getSizeBytes())
                        .setVersion(user.getVersion())
        );
        if (updated == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "用户配额更新失败，请重试");
        }

        log.info("文件上传成功: userId={}, fileId={}, name={}, sizeBytes={}",
                userId, fileNode.getId(), resolvedName, fileObject.getSizeBytes());
        return toNodeResponse(fileNode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadCheckResponse checkUpload(Long userId, UploadCheckRequest request) {
        User user = validateAndGetUser(userId);
        validateFileName(request.getName());
        validateParentFolder(request.getParentId(), userId);
        ConflictPolicyEnum policy = ConflictPolicyEnum.fromValue(request.getConflictPolicy());
        if (policy == ConflictPolicyEnum.REPLACE) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "V1 暂不支持 REPLACE 策略，请使用 REJECT 或 RENAME");
        }
        if (request.getSizeBytes() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }
        if (user.getUsedBytes() + request.getSizeBytes() > user.getQuotaBytes()) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED);
        }
        String sha256 = request.getSha256().toLowerCase(Locale.ROOT);
        FileObject fileObject = fileObjectService.findByChecksumAndSize(sha256, request.getSizeBytes());
        if (fileObject == null) {
            return UploadCheckResponse.miss();
        }

        String resolvedName = resolveNameConflict(request.getParentId(), userId, request.getName(), policy);
        fileObjectService.retain(fileObject.getId());
        FileNode fileNode = new FileNode()
                .setOwnerId(userId)
                .setParentId(request.getParentId())
                .setNodeType("FILE")
                .setName(resolvedName)
                .setSizeBytes(fileObject.getSizeBytes())
                .setFileObjectId(fileObject.getId())
                .setContentType(fileObject.getContentType())
                .setStorageKey(fileObject.getStorageKey())
                .setChecksum(fileObject.getChecksum())
                .setStatus("ACTIVE");
        fileNodeMapper.insert(fileNode);
        int updated = userMapper.updateById(new User()
                .setId(userId)
                .setUsedBytes(user.getUsedBytes() + fileObject.getSizeBytes())
                .setVersion(user.getVersion()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "用户配额更新失败，请重试");
        }
        log.info("文件秒传成功: userId={}, fileId={}, name={}, checksum={}",
                userId, fileNode.getId(), resolvedName, sha256);
        return UploadCheckResponse.hit(toNodeResponse(fileNode));
    }

    @Override
    public FileResource downloadFile(Long userId, Long fileId) {
        FileNode fileNode = getFileNode(userId, fileId);
        FileObject fileObject = fileObjectService.getRequired(fileNode.getFileObjectId());
        InputStream inputStream = storageService.load(fileObject.getStorageKey());
        return new FileResource(inputStream, fileNode.getContentType(), fileNode.getName(), fileNode.getSizeBytes());
    }

    private FileNode getFileNode(Long userId, Long fileId) {
        FileNode fileNode = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getId, fileId)
                        .eq(FileNode::getOwnerId, userId)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (fileNode == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        if (!"FILE".equals(fileNode.getNodeType())) {
            throw new BusinessException(ResultCode.INVALID_OPERATION, "该节点不是文件");
        }
        return fileNode;
    }

    private User validateAndGetUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户已被禁用");
        }
        return user;
    }

    private void validateFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "文件名不能为空");
        }
        if (filename.length() > 255) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "文件名长度不能超过 255 个字符");
        }
        if (".".equals(filename) || "..".equals(filename)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "文件名不能为 . 或 ..");
        }
        if (filename.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "文件名不能包含 \\ / : * ? \" < > | 等特殊字符");
        }
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

    private String resolveNameConflict(Long parentId, Long userId, String name, ConflictPolicyEnum policy) {
        boolean exists = fileNodeMapper.exists(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(parentId != null, FileNode::getParentId, parentId)
                        .isNull(parentId == null, FileNode::getParentId)
                        .eq(FileNode::getName, name)
                        .eq(FileNode::getStatus, "ACTIVE")
        );
        if (!exists) {
            return name;
        }
        if (policy == ConflictPolicyEnum.RENAME) {
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
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getOwnerId, userId)
                        .eq(parentId != null, FileNode::getParentId, parentId)
                        .isNull(parentId == null, FileNode::getParentId)
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
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

}

package com.pei.zfile.file.service;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.CopyNodesRequest;
import com.pei.zfile.file.dto.UploadCheckRequest;
import com.pei.zfile.file.dto.UploadFileRequest;
import com.pei.zfile.file.dto.UploadCheckResponse;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.service.Impl.FileServiceImpl;
import com.pei.zfile.file.service.Impl.NodeServiceImpl;
import com.pei.zfile.file.service.FileObjectService;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.storage.model.StoreResult;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAndNodeServiceTest {

    @Test
    void uploadNameConflictDoesNotWritePhysicalFile() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileServiceImpl service = new FileServiceImpl();
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        when(userMapper.selectById(1L)).thenReturn(activeUser());
        when(fileNodeMapper.exists(any())).thenReturn(true);

        UploadFileRequest request = new UploadFileRequest();
        request.setConflictPolicy("REJECT");
        request.setFile(new MockMultipartFile("file", "same.txt", "text/plain", new byte[]{1}));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.uploadFile(1L, request));

        assertEquals(ResultCode.FILE_NAME_CONFLICT, exception.getResultCode());
        verify(storageService, never()).storeTemp(any());
    }

    @Test
    void uploadCheckCreatesNodeWithoutReceivingFileWhenChecksumExists() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectService fileObjectService = mock(FileObjectService.class);
        FileServiceImpl service = service(fileNodeMapper, userMapper, storageService, fileObjectService);
        FileNode existing = new FileNode()
                .setId(20L)
                .setSizeBytes(4L)
                .setContentType("text/plain")
                .setStorageKey("existing-key")
                .setChecksum("a".repeat(64))
                .setNodeType("FILE")
                .setStatus("ACTIVE");
        when(userMapper.selectById(1L)).thenReturn(activeUser());
        when(fileNodeMapper.exists(any())).thenReturn(false);
        when(fileObjectService.findByChecksumAndSize(anyString(), eq(4L))).thenReturn(new FileObject()
                .setId(20L).setSizeBytes(4L).setContentType("text/plain")
                .setStorageKey("existing-key").setChecksum("a".repeat(64)));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UploadCheckRequest request = new UploadCheckRequest()
                .setName("copy.txt")
                .setSizeBytes(4L)
                .setSha256("a".repeat(64))
                .setConflictPolicy("RENAME");

        TransactionSynchronizationManager.initSynchronization();
        try {
            UploadCheckResponse response = service.checkUpload(1L, request);

            assertEquals(true, response.isInstantUploaded());
            verify(fileObjectService).retain(20L);
            verify(storageService, never()).copy(any(), any());
            verify(storageService, never()).storeTemp(any());
            verify(fileNodeMapper).insert(any(FileNode.class));
            verify(userMapper).updateById(any(User.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadCheckReturnsMissWithoutCreatingNodeWhenChecksumDoesNotExist() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectService fileObjectService = mock(FileObjectService.class);
        FileServiceImpl service = service(fileNodeMapper, userMapper, storageService, fileObjectService);
        when(userMapper.selectById(1L)).thenReturn(activeUser());
        when(fileNodeMapper.exists(any())).thenReturn(false);
        when(fileObjectService.findByChecksumAndSize(anyString(), eq(4L))).thenReturn(null);

        UploadCheckRequest request = new UploadCheckRequest()
                .setName("new.txt")
                .setSizeBytes(4L)
                .setSha256("b".repeat(64))
                .setConflictPolicy("RENAME");

        UploadCheckResponse response = service.checkUpload(1L, request);

        assertEquals(false, response.isInstantUploaded());
        verify(storageService, never()).copy(any(), any());
        verify(fileNodeMapper, never()).insert(any(FileNode.class));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void uploadRejectsClientChecksumMismatchAndDeletesTemporaryFile() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectService fileObjectService = mock(FileObjectService.class);
        FileServiceImpl service = service(fileNodeMapper, userMapper, storageService, fileObjectService);
        when(userMapper.selectById(1L)).thenReturn(activeUser());
        when(fileNodeMapper.exists(any())).thenReturn(false);
        when(storageService.storeTemp(any())).thenReturn(new StoreResult(
                "temp-key", "a".repeat(64), 1L));

        UploadFileRequest request = new UploadFileRequest();
        request.setConflictPolicy("RENAME");
        request.setSha256("b".repeat(64));
        request.setFile(new MockMultipartFile("file", "mismatch.txt", "text/plain", new byte[]{1}));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.uploadFile(1L, request));

        assertEquals(ResultCode.VALIDATION_ERROR, exception.getResultCode());
        verify(storageService).deleteTemp("temp-key");
        verify(storageService, never()).commitTemp(any(), any());
        verify(fileNodeMapper, never()).insert(any(FileNode.class));
    }

    @Test
    void uploadAfterPrecheckMissStoresUploadedFileInsteadOfCheckingForDuplicateAgain() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectService fileObjectService = mock(FileObjectService.class);
        FileServiceImpl service = service(fileNodeMapper, userMapper, storageService, fileObjectService);
        FileNode existing = new FileNode()
                .setSizeBytes(1L)
                .setContentType("text/plain")
                .setStorageKey("existing-key")
                .setChecksum("a".repeat(64))
                .setNodeType("FILE")
                .setStatus("ACTIVE");
        when(userMapper.selectById(1L)).thenReturn(activeUser());
        when(fileNodeMapper.exists(any())).thenReturn(false);
        when(fileObjectService.createOrRetain(any(), eq("text/plain"))).thenReturn(new FileObject()
                .setId(7L).setSizeBytes(1L).setContentType("text/plain")
                .setStorageKey("existing-key").setChecksum("a".repeat(64)));
        when(storageService.storeTemp(any())).thenReturn(new StoreResult(
                "temp-key", "a".repeat(64), 1L));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UploadFileRequest request = new UploadFileRequest();
        request.setConflictPolicy("RENAME");
        request.setFile(new MockMultipartFile("file", "uploaded.txt", "text/plain", new byte[]{1}));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadFile(1L, request);

            verify(fileObjectService).createOrRetain(any(), eq("text/plain"));
            verify(storageService, never()).copy(any(), any());
            verify(fileNodeMapper, never()).selectOne(any());
            verify(fileNodeMapper).insert(any(FileNode.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private FileServiceImpl service(FileNodeMapper fileNodeMapper,
                                    UserMapper userMapper,
                                    StorageService storageService) {
        return service(fileNodeMapper, userMapper, storageService, mock(FileObjectService.class));
    }

    private FileServiceImpl service(FileNodeMapper fileNodeMapper,
                                    UserMapper userMapper,
                                    StorageService storageService,
                                    FileObjectService fileObjectService) {
        FileServiceImpl service = new FileServiceImpl();
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "fileObjectService", fileObjectService);
        return service;
    }

    @Test
    void folderCannotBeCopiedIntoItself() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectService fileObjectService = mock(FileObjectService.class);
        NodeServiceImpl service = new NodeServiceImpl();
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "fileObjectService", fileObjectService);
        FileNode folder = new FileNode()
                .setId(10L)
                .setOwnerId(1L)
                .setNodeType("FOLDER")
                .setStatus("ACTIVE")
                .setSizeBytes(0L);
        when(fileNodeMapper.selectOne(any())).thenReturn(folder);
        when(userMapper.selectById(1L)).thenReturn(activeUser());

        CopyNodesRequest request = new CopyNodesRequest();
        request.setNodeIds(List.of(10L));
        request.setTargetParentId(10L);
        request.setConflictPolicy("REJECT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.copyNodes(1L, request));

        assertEquals(ResultCode.INVALID_OPERATION, exception.getResultCode());
        verify(storageService, never()).copy(any(), any());
    }

    private User activeUser() {
        return new User()
                .setId(1L)
                .setStatus("ACTIVE")
                .setRole("USER")
                .setQuotaBytes(1024L)
                .setUsedBytes(0L)
                .setVersion(0);
    }
}

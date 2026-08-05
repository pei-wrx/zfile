package com.pei.zfile.file.service;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.CopyNodesRequest;
import com.pei.zfile.file.dto.UploadFileRequest;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.service.Impl.FileServiceImpl;
import com.pei.zfile.file.service.Impl.NodeServiceImpl;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void folderCannotBeCopiedIntoItself() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        StorageService storageService = mock(StorageService.class);
        NodeServiceImpl service = new NodeServiceImpl();
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "storageService", storageService);
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

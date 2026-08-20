package com.pei.zfile.share.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.service.FileObjectService;
import com.pei.zfile.share.entity.Share;
import com.pei.zfile.share.entity.ShareItem;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.mapper.ShareItemMapper;
import com.pei.zfile.share.mapper.ShareMapper;
import com.pei.zfile.share.service.Impl.ShareServiceImpl;
import com.pei.zfile.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareServiceImplTest {

    private ShareServiceImpl service;
    private ShareMapper shareMapper;
    private ShareItemMapper shareItemMapper;
    private FileNodeMapper fileNodeMapper;
    private StorageService storageService;
    private FileObjectService fileObjectService;

    @BeforeEach
    void setUp() {
        service = new ShareServiceImpl();
        shareMapper = mock(ShareMapper.class);
        shareItemMapper = mock(ShareItemMapper.class);
        fileNodeMapper = mock(FileNodeMapper.class);
        storageService = mock(StorageService.class);
        fileObjectService = mock(FileObjectService.class);
        ReflectionTestUtils.setField(service, "shareMapper", shareMapper);
        ReflectionTestUtils.setField(service, "shareItemMapper", shareItemMapper);
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "fileObjectService", fileObjectService);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(10L)
                .setShareCode("share123")
                .setStatus("ACTIVE")
                .setDownloadCount(0);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(shareItemMapper.selectList(any())).thenReturn(List.of(
                new ShareItem().setShareId(1L).setNodeId(100L)));
    }

    @Test
    void cannotDownloadOwnersUnsharedFileByGuessingId() {
        FileNode privateFile = activeNode(999L, 200L, "FILE").setStorageKey("private-key");
        FileNode privateFolder = activeNode(200L, null, "FOLDER");
        when(fileNodeMapper.selectOne(any())).thenReturn(privateFile);
        when(fileNodeMapper.selectById(200L)).thenReturn(privateFolder);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.downloadShareFile("share123", 999L, null));

        assertEquals(ResultCode.FILE_NOT_FOUND, exception.getResultCode());
        verify(storageService, never()).load(any());
    }

    @Test
    void cannotBrowseOwnersUnsharedFolderByGuessingId() {
        FileNode privateFolder = activeNode(200L, null, "FOLDER");
        when(fileNodeMapper.selectById(200L)).thenReturn(privateFolder);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.browseShareNodes("share123", 200L, null));

        assertEquals(ResultCode.FILE_NOT_FOUND, exception.getResultCode());
    }

    @Test
    void previewDoesNotIncrementDownloadCount() {
        FileNode sharedFile = activeNode(100L, null, "FILE")
                .setFileObjectId(7L)
                .setName("shared.txt")
                .setContentType("text/plain");
        when(fileNodeMapper.selectOne(any())).thenReturn(sharedFile);
        when(fileObjectService.getRequired(7L)).thenReturn(new FileObject().setStorageKey("shared-key"));
        when(storageService.load("shared-key")).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        service.previewShareFile("share123", 100L, null);

        verify(shareMapper, never()).update(any(), any());
    }

    @Test
    void downloadIncrementsDownloadCountOnce() {
        FileNode sharedFile = activeNode(100L, null, "FILE")
                .setFileObjectId(7L)
                .setName("shared.txt")
                .setContentType("text/plain");
        when(fileNodeMapper.selectOne(any())).thenReturn(sharedFile);
        when(fileObjectService.getRequired(7L)).thenReturn(new FileObject().setStorageKey("shared-key"));
        when(storageService.load("shared-key")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(shareMapper.update(any(), any())).thenReturn(1);

        service.downloadShareFile("share123", 100L, null);

        verify(shareMapper, times(1)).update(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsPublicSharesAsPagedPublicResponses() {
        Share publicShare = new Share()
                .setId(2L)
                .setShareCode("public12")
                .setTitle("Public share")
                .setPasswordHash("password-hash")
                .setExpiresAt(Instant.now().plusSeconds(3600))
                .setDownloadLimit(10)
                .setDownloadCount(2)
                .setStatus("ACTIVE")
                .setCreatedAt(LocalDateTime.now());
        Page<Share> mapperPage = new Page<Share>(2, 12, 13)
                .setRecords(List.of(publicShare));
        when(shareMapper.selectPage(any(Page.class), any())).thenReturn(mapperPage);
        ReflectionTestUtils.setField(service, "shareBaseUrl", "http://localhost:5173");

        PageResult<PublicShareResponse> result = service.listPublicShares(2, 12);

        assertEquals(2, result.getPage());
        assertEquals(12, result.getSize());
        assertEquals(13, result.getTotal());
        assertEquals(2, result.getPages());
        assertEquals(1, result.getItems().size());
        PublicShareResponse item = result.getItems().getFirst();
        assertEquals("public12", item.getShareCode());
        assertEquals("http://localhost:5173/s/public12", item.getShareUrl());
        assertTrue(item.getPasswordRequired());
        assertEquals(2, item.getDownloadCount());
    }

    private FileNode activeNode(Long id, Long parentId, String type) {
        return new FileNode()
                .setId(id)
                .setOwnerId(10L)
                .setParentId(parentId)
                .setNodeType(type)
                .setStatus("ACTIVE")
                .setSizeBytes(1L);
    }
}

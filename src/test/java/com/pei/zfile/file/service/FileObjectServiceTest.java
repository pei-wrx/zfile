package com.pei.zfile.file.service;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.mapper.FileObjectMapper;
import com.pei.zfile.file.service.Impl.FileObjectServiceImpl;
import com.pei.zfile.storage.model.StoreResult;
import com.pei.zfile.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileObjectServiceTest {

    @Test
    void createOrRetainReusesExistingObjectWithoutCopyingPhysicalFile() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectServiceImpl service = service(mapper, storageService);
        FileObject existing = object(7L, 2L);
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.retain(7L)).thenReturn(1);

        StoreResult result = new StoreResult("temp-key", "a".repeat(64), 4L);
        FileObject actual = service.createOrRetain(result, "text/plain");

        assertEquals(7L, actual.getId());
        verify(storageService).deleteTemp("temp-key");
        verify(storageService, never()).commitTemp(anyString(), anyString());
        verify(storageService, never()).copy(anyString(), anyString());
        verify(mapper).retain(7L);
    }

    @Test
    void releaseReturnsObjectOnlyWhenLastReferenceIsRemoved() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectServiceImpl service = service(mapper, storageService);
        when(mapper.release(7L, 1L)).thenReturn(1);
        when(mapper.selectById(7L)).thenReturn(object(7L, 0L));

        List<FileObject> released = service.releaseReferences(List.of(7L));

        assertEquals(1, released.size());
        assertEquals("storage-key", released.getFirst().getStorageKey());
        verify(mapper).deleteById(7L);
    }

    @Test
    void releaseDoesNotDeleteObjectWhileReferencesRemain() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectServiceImpl service = service(mapper, storageService);
        when(mapper.release(7L, 1L)).thenReturn(1);
        when(mapper.selectById(7L)).thenReturn(object(7L, 1L));

        List<FileObject> released = service.releaseReferences(List.of(7L));

        assertEquals(0, released.size());
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void releaseRejectsMissingOrInsufficientReferences() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        StorageService storageService = mock(StorageService.class);
        FileObjectServiceImpl service = service(mapper, storageService);
        when(mapper.release(7L, 2L)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.releaseReferences(List.of(7L, 7L)));
    }

    private FileObjectServiceImpl service(FileObjectMapper mapper, StorageService storageService) {
        FileObjectServiceImpl service = new FileObjectServiceImpl();
        ReflectionTestUtils.setField(service, "fileObjectMapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        return service;
    }

    private FileObject object(Long id, Long referenceCount) {
        return new FileObject()
                .setId(id)
                .setChecksum("a".repeat(64))
                .setSizeBytes(4L)
                .setContentType("text/plain")
                .setStorageKey("storage-key")
                .setReferenceCount(referenceCount);
    }
}

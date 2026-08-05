package com.pei.zfile.trash.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.trash.dto.TrashNodesRequest;
import com.pei.zfile.trash.service.Impl.TrashServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrashServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FileNode.class
        );
    }

    @Test
    void emptyTrashBuildsValidRootQuery() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        TrashServiceImpl service = createService(fileNodeMapper);
        when(fileNodeMapper.selectList(any())).thenReturn(List.of());
        when(fileNodeMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<FileNode>(1, 20, 0).setRecords(List.of()));

        PageResult<NodeResponse> result = service.selectRootNode(1L, new TrashNodesRequest());

        ArgumentCaptor<Wrapper<FileNode>> wrapperCaptor = wrapperCaptor();
        verify(fileNodeMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertEquals(0, result.getTotal());
        assertTrue(sqlSegment.contains("parent_id IS NULL"));
        assertFalse(sqlSegment.matches("(?s).*\\bOR\\s*\\).*"));
    }

    @Test
    void trashRootQueryExcludesChildrenOfTrashedNodes() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        TrashServiceImpl service = createService(fileNodeMapper);
        when(fileNodeMapper.selectList(any())).thenReturn(List.of(
                new FileNode().setId(10L),
                new FileNode().setId(11L)
        ));
        when(fileNodeMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<FileNode>(1, 20, 0).setRecords(List.of()));

        service.selectRootNode(1L, new TrashNodesRequest());

        ArgumentCaptor<Wrapper<FileNode>> wrapperCaptor = wrapperCaptor();
        verify(fileNodeMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("parent_id IS NULL OR parent_id NOT IN"));
    }

    @Test
    void restoreExplicitlyClearsNullableTrashFields() {
        FileNodeMapper fileNodeMapper = mock(FileNodeMapper.class);
        TrashServiceImpl service = createService(fileNodeMapper);
        FileNode trashedFile = new FileNode()
                .setId(11L)
                .setOwnerId(1L)
                .setNodeType("FILE")
                .setName("photo.png")
                .setStatus("TRASHED")
                .setVersion(3)
                .setDeletedAt(LocalDateTime.now());
        when(fileNodeMapper.selectOne(any())).thenReturn(trashedFile);
        when(fileNodeMapper.exists(any())).thenReturn(false);
        when(fileNodeMapper.update(any(), any())).thenReturn(1);

        NodeResponse response = service.restoreNode(1L, 11L, new com.pei.zfile.trash.dto.RestoreNodeRequest());

        ArgumentCaptor<Wrapper<FileNode>> wrapperCaptor = wrapperCaptor();
        verify(fileNodeMapper).update(any(), wrapperCaptor.capture());
        LambdaUpdateWrapper<FileNode> updateWrapper = (LambdaUpdateWrapper<FileNode>) wrapperCaptor.getValue();
        String sqlSet = updateWrapper.getSqlSet();
        assertEquals("ACTIVE", response.getStatus());
        assertTrue(sqlSet.contains("deleted_at="));
        assertTrue(sqlSet.contains("original_parent_id="));
        assertEquals(4, trashedFile.getVersion());
    }

    private TrashServiceImpl createService(FileNodeMapper fileNodeMapper) {
        TrashServiceImpl service = new TrashServiceImpl();
        ReflectionTestUtils.setField(service, "fileNodeMapper", fileNodeMapper);
        return service;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Wrapper<FileNode>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }
}

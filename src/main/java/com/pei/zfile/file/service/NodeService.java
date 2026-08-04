package com.pei.zfile.file.service;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.file.dto.*;
import jakarta.validation.Valid;

import java.util.List;

public interface NodeService {

    NodeResponse createFolder(Long userId, CreateFolderRequest request);

    PageResult<NodeResponse> listNodes(Long userId, @Valid ListNodesRequest request);

    NodeDetailResponse getNodeDetails(Long userId, Long nodeId);

    NodeResponse renameNode(Long userId, Long nodeId, String name);

    void trashNode(Long userId, Long nodeId);

    void batchTrashNode(Long userId, List<Long> nodeIds);

    NodeResponse moveNode(Long userId, Long nodeId, @Valid MoveNodeRequest request);

    List<NodeResponse> copyNodes(Long userId, @Valid CopyNodesRequest request);
}
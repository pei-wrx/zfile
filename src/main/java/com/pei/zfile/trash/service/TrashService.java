package com.pei.zfile.trash.service;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.trash.dto.RestoreNodeRequest;
import com.pei.zfile.trash.dto.TrashNodesRequest;
import jakarta.validation.Valid;

public interface TrashService {
    PageResult<NodeResponse> selectRootNode(Long userId, @Valid TrashNodesRequest trashNodesRequest);

    void deleteRootNodes(Long userId);

    NodeResponse restoreNode(Long userId, Long nodeId, @Valid RestoreNodeRequest request);

    void deleteNode(Long userId, Long nodeId);
}
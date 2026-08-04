package com.pei.zfile.trash.controller;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.trash.dto.RestoreNodeRequest;
import com.pei.zfile.trash.dto.TrashNodesRequest;
import com.pei.zfile.trash.service.TrashService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/trash")
public class TrashController {

    @Autowired
    private TrashService trashService;

    /**
     * 查询回收站根节点
     */
    @GetMapping
    public Result<PageResult<NodeResponse>> selectRootNode(@Valid TrashNodesRequest trashNodesRequest) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        PageResult<NodeResponse> pageResult = trashService.selectRootNode(userId, trashNodesRequest);
        return Result.success(pageResult);
    }
    /**
     * 清空当前用户回收站
     */
    @DeleteMapping
    public Result<Void> deleteRootNode() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        trashService.deleteRootNodes(userId);
        return Result.success();
    }

    /**
     * 恢复指定的回收站节点
     */
    @PostMapping("/{nodeId}/restore")
    public Result<NodeResponse> restore(@PathVariable("nodeId") Long nodeId,
                                        @RequestBody(required = false) @Valid RestoreNodeRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeResponse response = trashService.restoreNode(userId, nodeId, request);
        return Result.success(response);
    }
    /**
     * 删除指定的回收站节点
     */
    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable("nodeId") Long nodeId) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        trashService.deleteNode(userId, nodeId);
        return Result.success();
    }
}

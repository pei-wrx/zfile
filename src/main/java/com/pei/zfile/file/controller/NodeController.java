package com.pei.zfile.file.controller;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.*;
import com.pei.zfile.file.service.NodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/nodes")
public class NodeController {

    @Autowired
    private NodeService nodeService;

    /**
     * 查询目录内容或搜索节点
     */
    @GetMapping
    public Result<PageResult<NodeResponse>> getContentOrNodes(@Valid ListNodesRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        PageResult<NodeResponse> pageResult = nodeService.listNodes(userId,request);
        return Result.success(pageResult);
    }
    /**
     * 查询节点详情
     */
    @GetMapping("/{nodeId}")
    public Result<NodeDetailResponse> getNodeDetails(@PathVariable("nodeId") Long nodeId){
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeDetailResponse nodeDetailResponse = nodeService.getNodeDetails(userId,nodeId);
        return Result.success(nodeDetailResponse);
    }
    /**
     * 重命名节点
     */
    @PatchMapping("/{nodeId}")
    public Result<NodeResponse> renameNode(@PathVariable("nodeId") Long nodeId, @RequestBody @Valid RenameNodeRequest request){
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeResponse nodeResponse = nodeService.renameNode(userId, nodeId, request.getName());
        return Result.success(nodeResponse);
    }
    /**
     * 将节点移入回收站
     */
    @DeleteMapping("/{nodeId}")
    public Result<Void> moveToTrash(@PathVariable("nodeId") Long nodeId){
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        nodeService.trashNode(userId, nodeId);
        return Result.success();
    }
    /**
     * 移动节点
     */
    @PostMapping("/{nodeId}/move")
    public Result<NodeResponse> moveNode(@PathVariable("nodeId") Long nodeId,
                                         @RequestBody @Valid MoveNodeRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeResponse response = nodeService.moveNode(userId, nodeId, request);
        return Result.success(response);
    }
    /**
     * 批量复制节点
     */
    @PostMapping("/copy")
    public Result<List<NodeResponse>> copyNode(@RequestBody @Valid CopyNodesRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        List<NodeResponse> response = nodeService.copyNodes(userId, request);
        return Result.success(response);
    }
    /**
     * 批量移入回收站
     */
    @PostMapping("/batch-delete")
    public Result<Void> batchDeleteNode(@RequestBody @Valid BatchDeleteRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        nodeService.batchTrashNode(userId, request.getNodeIds());
        return Result.success();
    }
}
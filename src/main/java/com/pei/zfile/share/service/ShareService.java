package com.pei.zfile.share.service;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.share.dto.CreateShareRequest;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.dto.ShareResponse;
import com.pei.zfile.share.dto.ShareVerifyRequest;
import com.pei.zfile.share.dto.ShareVerifyResponse;
import jakarta.validation.Valid;

import java.util.List;

public interface ShareService {

    ShareResponse create(Long userId, @Valid CreateShareRequest request);

    PageResult<ShareResponse> list(Long userId, String status, int page, int size);

    ShareResponse detail(Long userId, Long shareId);

    void cancel(Long userId, Long shareId);

    PublicShareResponse getPublicShareDetail(String shareCode);

    ShareVerifyResponse verifySharePassword(String shareCode, @Valid ShareVerifyRequest request);

    List<NodeResponse> browseShareNodes(String shareCode, Long parentId, String shareToken);

    FileResource downloadShareFile(String shareCode, Long fileId, String shareToken);
}
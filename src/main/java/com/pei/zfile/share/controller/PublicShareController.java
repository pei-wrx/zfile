package com.pei.zfile.share.controller;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.util.FileDownloadHelper;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.dto.ShareVerifyRequest;
import com.pei.zfile.share.dto.ShareVerifyResponse;
import com.pei.zfile.share.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/public/shares")
public class PublicShareController {

    private static final String SHARE_TOKEN_COOKIE = "share_token";

    @Autowired
    private ShareService shareService;

    /**
     * 分页查询当前有效且仍可下载的公开分享
     */
    @GetMapping
    public Result<PageResult<PublicShareResponse>> listPublicShares(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return Result.success(shareService.listPublicShares(page, size));
    }

    /**
     * 获取分享公开信息
     */
    @GetMapping("/{shareCode}")
    public Result<PublicShareResponse> getShareDetail(@PathVariable String shareCode) {
        PublicShareResponse shareResponse = shareService.getPublicShareDetail(shareCode);
        return Result.success(shareResponse);
    }
    /**
     * 验证分享口令
     */
    @PostMapping("/{shareCode}/verify")
    public Result<ShareVerifyResponse> verifyShareCode(@PathVariable String shareCode,
                                                        @RequestBody @Valid ShareVerifyRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        ShareVerifyResponse verifyResponse = shareService.verifySharePassword(
                shareCode, request, httpRequest.getRemoteAddr());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildShareTokenCookie(
                shareCode, verifyResponse.getShareToken(), verifyResponse.getExpiresIn(), httpRequest.isSecure()));
        return Result.success(verifyResponse);
    }
    /**
     * 浏览分享内容
     */
    @GetMapping("/{shareCode}/nodes")
    public Result<List<NodeResponse>> browseShareNodes(@PathVariable String shareCode,
                                                        @RequestParam(required = false) Long parentId,
                                                        @RequestHeader(value = "X-Share-Token", required = false) String shareToken,
                                                        @CookieValue(value = SHARE_TOKEN_COOKIE, required = false) String shareTokenCookie) {
        List<NodeResponse> nodes = shareService.browseShareNodes(
                shareCode, parentId, resolveShareToken(shareToken, shareTokenCookie));
        return Result.success(nodes);
    }
    /**
     * 下载分享中的文件
     */
    @GetMapping("/{shareCode}/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> downloadShareFile(@PathVariable String shareCode,
                                                                  @PathVariable Long fileId,
                                                                  @RequestHeader(value = "X-Share-Token", required = false) String shareToken,
                                                                  @CookieValue(value = SHARE_TOKEN_COOKIE, required = false) String shareTokenCookie,
                                                                  HttpServletRequest request) {
        FileResource resource = shareService.downloadShareFile(
                shareCode, fileId, resolveShareToken(shareToken, shareTokenCookie));
        return FileDownloadHelper.buildResponse(resource, request, false);
    }

    /**
     * 内联预览分享中的文件，不计入下载次数
     */
    @GetMapping("/{shareCode}/files/{fileId}/preview")
    public ResponseEntity<InputStreamResource> previewShareFile(@PathVariable String shareCode,
                                                                 @PathVariable Long fileId,
                                                                 @RequestHeader(value = "X-Share-Token", required = false) String shareToken,
                                                                 @CookieValue(value = SHARE_TOKEN_COOKIE, required = false) String shareTokenCookie,
                                                                 HttpServletRequest request) {
        FileResource resource = shareService.previewShareFile(
                shareCode, fileId, resolveShareToken(shareToken, shareTokenCookie));
        return FileDownloadHelper.buildResponse(resource, request, true);
    }

    private String resolveShareToken(String headerToken, String cookieToken) {
        return headerToken != null && !headerToken.isBlank() ? headerToken : cookieToken;
    }

    private String buildShareTokenCookie(String shareCode, String shareToken,
                                         Long expiresIn, boolean secure) {
        return ResponseCookie.from(SHARE_TOKEN_COOKIE, shareToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1/public/shares/"
                        + UriUtils.encodePathSegment(shareCode, StandardCharsets.UTF_8))
                .maxAge(Duration.ofSeconds(expiresIn))
                .build()
                .toString();
    }
}

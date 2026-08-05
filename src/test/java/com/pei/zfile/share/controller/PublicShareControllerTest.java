package com.pei.zfile.share.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.dto.ShareVerifyRequest;
import com.pei.zfile.share.dto.ShareVerifyResponse;
import com.pei.zfile.share.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicShareControllerTest {

    private PublicShareController controller;
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        controller = new PublicShareController();
        shareService = mock(ShareService.class);
        ReflectionTestUtils.setField(controller, "shareService", shareService);
    }

    @Test
    void verifySetsShareScopedHttpOnlyCookie() {
        ShareVerifyRequest verifyRequest = new ShareVerifyRequest();
        verifyRequest.setPassword("1234");
        ShareVerifyResponse tokenResponse = ShareVerifyResponse.builder()
                .shareToken("signed-token")
                .expiresIn(1800L)
                .build();
        when(shareService.verifySharePassword("share123", verifyRequest, "127.0.0.1"))
                .thenReturn(tokenResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.verifyShareCode("share123", verifyRequest, request, response);

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("share_token=signed-token"));
        assertTrue(setCookie.contains("Max-Age=1800"));
        assertTrue(setCookie.contains("Path=/api/v1/public/shares/share123"));
        assertTrue(setCookie.contains("HttpOnly"));
    }

    @Test
    void cookieTokenIsUsedWhenHeaderIsMissing() {
        when(shareService.browseShareNodes("share123", null, "cookie-token"))
                .thenReturn(List.of(NodeResponse.builder().id(1L).build()));

        controller.browseShareNodes("share123", null, null, "cookie-token");

        verify(shareService).browseShareNodes("share123", null, "cookie-token");
    }

    @Test
    void publicShareDetailExposesPasswordRequiredField() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                PublicShareResponse.builder().passwordRequired(true).build());

        assertTrue(json.contains("\"passwordRequired\":true"));
        assertTrue(json.contains("\"hasPassword\":true"));
    }

    @Test
    void listsPublicSharesWithRequestedPagination() {
        PageResult<PublicShareResponse> page = PageResult.of(
                List.of(PublicShareResponse.builder().shareCode("share123").build()),
                2, 12, 13);
        when(shareService.listPublicShares(2, 12)).thenReturn(page);

        var result = controller.listPublicShares(2, 12);

        assertSame(page, result.getData());
        verify(shareService).listPublicShares(2, 12);
    }
}

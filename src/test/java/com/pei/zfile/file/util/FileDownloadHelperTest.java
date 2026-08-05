package com.pei.zfile.file.util;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.FileResource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDownloadHelperTest {

    @Test
    void rangeResponseContainsOnlyRequestedBytes() throws Exception {
        FileResource resource = resource("text/plain");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Range", "bytes=2-4");

        ResponseEntity<InputStreamResource> response =
                FileDownloadHelper.buildResponse(resource, request, false);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes 2-4/10", response.getHeaders().getFirst("Content-Range"));
        assertArrayEquals(new byte[]{2, 3, 4}, response.getBody().getInputStream().readAllBytes());
    }

    @Test
    void invalidRangeReturns416() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Range", "bytes=50-60");

        ResponseEntity<InputStreamResource> response =
                FileDownloadHelper.buildResponse(resource("text/plain"), request, false);

        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
        assertEquals("bytes */10", response.getHeaders().getFirst("Content-Range"));
    }

    @Test
    void activeContentTypeCannotBePreviewedInline() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FileDownloadHelper.buildResponse(
                        resource("text/html"), new MockHttpServletRequest(), true));

        assertEquals(ResultCode.UNSUPPORTED_MEDIA_TYPE, exception.getResultCode());
    }

    @Test
    void videoCanBePreviewedInlineWithRangeRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Range", "bytes=2-4");

        ResponseEntity<InputStreamResource> response =
                FileDownloadHelper.buildResponse(resource("video/mp4", "clip.mp4"), request, true);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("video/mp4", response.getHeaders().getFirst("Content-Type"));
        assertEquals("bytes 2-4/10", response.getHeaders().getFirst("Content-Range"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("inline;"));
        assertArrayEquals(new byte[]{2, 3, 4}, response.getBody().getInputStream().readAllBytes());
    }

    @Test
    void commonVideoExtensionGetsPreviewContentTypeWhenUploadMimeIsGeneric() {
        ResponseEntity<InputStreamResource> response = FileDownloadHelper.buildResponse(
                resource("application/octet-stream", "clip.webm"),
                new MockHttpServletRequest(),
                true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("video/webm", response.getHeaders().getFirst("Content-Type"));
    }

    private FileResource resource(String contentType) {
        return resource(contentType, "test.txt");
    }

    private FileResource resource(String contentType, String fileName) {
        return new FileResource(
                new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                contentType,
                fileName,
                10);
    }
}

package com.pei.zfile.file.util;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.dto.FileResource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class FileDownloadHelper {

    private FileDownloadHelper() {
    }

    public static ResponseEntity<InputStreamResource> buildResponse(FileResource resource,
                                                                     HttpServletRequest request,
                                                                     boolean inline) {
        String rangeHeader = request.getHeader("Range");
        long fileSize = resource.getSizeBytes();

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return fullResponse(resource, inline);
        }

        RangeInfo range = parseRange(rangeHeader, fileSize);
        if (range == null) {
            return fullResponse(resource, inline);
        }

        long contentLength = range.end - range.start + 1;
        InputStream inputStream = resource.getInputStream();
        try {
            inputStream.skipNBytes(range.start);
            InputStreamResource body = new InputStreamResource(inputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentTypeOrDefault(resource.getContentType()));
            headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start + "-" + range.end + "/" + fileSize);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            setContentDisposition(headers, resource.getFileName(), inline);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
        } catch (Exception e) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
            throw new BusinessException(ResultCode.STORAGE_ERROR, "文件读取失败", e);
        }
    }

    private static ResponseEntity<InputStreamResource> fullResponse(FileResource resource, boolean inline) {
        InputStreamResource body = new InputStreamResource(resource.getInputStream());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentTypeOrDefault(resource.getContentType()));
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.getSizeBytes()));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        setContentDisposition(headers, resource.getFileName(), inline);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static void setContentDisposition(HttpHeaders headers, String fileName, boolean inline) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = inline ? "inline" : "attachment";
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                disposition + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
    }

    private static String contentTypeOrDefault(String contentType) {
        return contentType != null ? contentType : "application/octet-stream";
    }

    private static RangeInfo parseRange(String rangeHeader, long fileSize) {
        try {
            String rangeValue = rangeHeader.substring("bytes=".length());
            String[] parts = rangeValue.split("-", 2);
            if (parts.length != 2) {
                return null;
            }
            long start;
            long end;
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]);
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                if (start >= fileSize) {
                    return null;
                }
                if (parts[1].isEmpty()) {
                    end = fileSize - 1;
                } else {
                    end = Long.parseLong(parts[1]);
                    if (end >= fileSize) {
                        end = fileSize - 1;
                    }
                }
            }
            if (start > end) {
                return null;
            }
            return new RangeInfo(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RangeInfo(long start, long end) {
    }
}
package com.pei.zfile.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

@Data
@AllArgsConstructor
public class FileResource {

    private InputStream inputStream;

    private String contentType;

    private String fileName;

    private long sizeBytes;
}
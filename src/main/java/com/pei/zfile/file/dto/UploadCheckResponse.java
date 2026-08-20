package com.pei.zfile.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadCheckResponse {

    private boolean instantUploaded;

    private NodeResponse node;

    public static UploadCheckResponse miss() {
        return new UploadCheckResponse(false, null);
    }

    public static UploadCheckResponse hit(NodeResponse node) {
        return new UploadCheckResponse(true, node);
    }
}

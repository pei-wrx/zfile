package com.pei.zfile.share.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicShareResponse {

    private String shareCode;

    private String shareUrl;

    private String title;

    private Boolean passwordRequired;

    /**
     * Backward-compatible response alias for clients using the old field name.
     */
    @JsonProperty("hasPassword")
    public Boolean getHasPassword() {
        return passwordRequired;
    }

    private Instant expiresAt;

    private Integer downloadLimit;

    private Integer downloadCount;

    private String status;

    private LocalDateTime createdAt;
}

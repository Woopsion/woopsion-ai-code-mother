package com.woopsion.woopsionaicodemother.model.dto.app;

import lombok.Data;

/**
 * @author wangpengcan
 * @date 2025/10/28
 * @time 01:25
 * @description
 */
@Data
public class AppGenCodeRequest {
    private Long appId;
    private String message;
}

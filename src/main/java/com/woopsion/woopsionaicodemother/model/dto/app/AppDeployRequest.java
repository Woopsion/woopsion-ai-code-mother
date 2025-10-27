package com.woopsion.woopsionaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wangpengcan
 * @date 2025/10/28
 * @time 00:05
 * @description
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}

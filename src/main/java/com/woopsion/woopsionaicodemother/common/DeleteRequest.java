package com.woopsion.woopsionaicodemother.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wangpengcan
 * @date 2025/10/16
 * @time 23:37
 * @description
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}


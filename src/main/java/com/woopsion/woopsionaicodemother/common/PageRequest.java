package com.woopsion.woopsionaicodemother.common;

import lombok.Data;

/**
 * @author wangpengcan
 * @date 2025/10/16
 * @time 23:36
 * @description
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int pageNum = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}


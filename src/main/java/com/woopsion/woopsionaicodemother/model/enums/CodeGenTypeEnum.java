package com.woopsion.woopsionaicodemother.model.enums;

import lombok.Getter;

/**
 * @author wangpengcan
 * @date 2025/10/26
 * @time 16:25
 * @description
 */
@Getter
public enum CodeGenTypeEnum {
    HTML("原生 HTML 模式", "user"),
    MULTI_FILE("原生多文件模式", "multi_files");

    private final String text;

    private final String value;
    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}

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
    HTML("原生 HTML 模式", "html"),
    MULTI_FILE("原生多文件模式", "multi_files"),
    VUE_PROJECT("Vue 工程模式", "vue_project");


    private final String text;

    private final String value;
    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }

    public static CodeGenTypeEnum getEnumByValue(String codeGenTypeStr) {
        for (CodeGenTypeEnum codeGenTypeEnum : CodeGenTypeEnum.values()) {
            if (codeGenTypeEnum.getValue().equals(codeGenTypeStr)) {
                return codeGenTypeEnum;
            }
        }
        return null;
    }
}

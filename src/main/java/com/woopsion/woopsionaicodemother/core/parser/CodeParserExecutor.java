package com.woopsion.woopsionaicodemother.core.parser;

import com.woopsion.woopsionaicodemother.exception.BusinessException;
import com.woopsion.woopsionaicodemother.exception.ErrorCode;
import com.woopsion.woopsionaicodemother.model.enums.CodeGenTypeEnum;

/**
 * @author wangpengcan
 * @date 2025/10/27
 * @time 00:10
 * @description
 */
public class CodeParserExecutor {

    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     *
     * @param codeContent 代码内容
     * @param codeGenType 代码生成类型
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}


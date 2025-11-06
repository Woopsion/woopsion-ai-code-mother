package com.woopsion.woopsionaicodemother.ai;

import com.woopsion.woopsionaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * @author wangpengcan
 * @date 2025/11/3
 * @time 15:09
 * @description
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}

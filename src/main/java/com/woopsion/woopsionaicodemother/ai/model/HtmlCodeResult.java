package com.woopsion.woopsionaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * @author wangpengcan
 * @date 2025/10/26
 * @time 16:10
 * @description 定义机构化输出内容对象 langchain4j会将我们定义的接收bean对象在提问时转化为提示词的要求
 */
@Description("生成 HTML 代码文件的结果")
@Data
public class HtmlCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("生成代码的描述")
    private String description;

    public String getHtmlCode() {
        return htmlCode;
    }

    public String getDescription() {
        return description;
    }

    public void setHtmlCode(String htmlCode) {
        this.htmlCode = htmlCode;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}


package com.woopsion.woopsionaicodemother.core;

import com.woopsion.woopsionaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("任务记录网站", CodeGenTypeEnum.MULTI_FILE,123456789L);
        Assertions.assertNotNull(file);
    }
/**
 * 测试生成并保存代码流的方法
 * 该测试方法验证AI代码生成器能否生成并保存多文件代码流
 */
    @Test
    void generateAndSaveCodeStream() {
    // 调用AI代码生成器门面类生成并保存代码流，指定任务为"任务记录网站"，代码类型为MULTI_FILE
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("任务记录网站", CodeGenTypeEnum.MULTI_FILE,123456789L);
        // 阻塞等待所有数据收集完成，将流式数据转换为List集合
        List<String> result = codeStream.collectList().block();
        // 验证结果列表不为空
        Assertions.assertNotNull(result);
    // 将所有结果字符串连接成一个完整的代码内容
        String completeContent = String.join("", result);
    // 验证连接后的完整内容不为空
        Assertions.assertNotNull(completeContent);
    }

    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 2L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }



}

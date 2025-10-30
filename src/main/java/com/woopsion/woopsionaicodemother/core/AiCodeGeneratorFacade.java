package com.woopsion.woopsionaicodemother.core;

/**
 * @author wangpengcan
 * @date 2025/10/26
 * @time 16:36
 * @description
 */

import cn.hutool.json.JSONUtil;
import com.woopsion.woopsionaicodemother.ai.AiCodeGeneratorService;
import com.woopsion.woopsionaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.woopsion.woopsionaicodemother.ai.model.HtmlCodeResult;
import com.woopsion.woopsionaicodemother.ai.model.MultiFileCodeResult;
import com.woopsion.woopsionaicodemother.core.parser.CodeParserExecutor;
import com.woopsion.woopsionaicodemother.core.saver.CodeFileSaverExecutor;
import com.woopsion.woopsionaicodemother.exception.BusinessException;
import com.woopsion.woopsionaicodemother.exception.ErrorCode;
import com.woopsion.woopsionaicodemother.model.enums.CodeGenTypeEnum;
import com.woopsion.woopsionaicodemother.model.message.AiResponseMessage;
import com.woopsion.woopsionaicodemother.model.message.ToolExecutedMessage;
import com.woopsion.woopsionaicodemother.model.message.ToolRequestMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;



    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                Flux<String> codeStream = processTokenStream(tokenStream);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };

    }


    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    if (chunk != null && !chunk.isEmpty()) {
                        log.debug("收集到代码片段: {} 字符", chunk.length());
                        codeBuilder.append(chunk);
                    }
                })
                .doOnError(error -> {
                    // 发生错误时记录日志
                    log.error("流式处理错误: {}", error.getMessage(), error);
                })
                .doOnComplete(() -> {
                    // 流式返回完成后异步保存代码，避免阻塞流式传输
                    log.info("流式处理完成，开始保存代码，总长度: {}", codeBuilder.length());
                    new Thread(() -> {
                        try {
                            if (codeBuilder.length() == 0) {
                                log.warn("生成的代码为空，跳过保存");
                                return;
                            }
                            String completeCode = codeBuilder.toString();
                            // 使用执行器解析代码
                            Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                            // 使用执行器保存代码
                            File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                            log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                        } catch (Exception e) {
                            log.error("保存失败: {}", e.getMessage(), e);
                        }
                    }).start();
                });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .beforeToolExecution((BeforeToolExecution beforeToolExecution) -> {
                        ToolExecutionRequest request = beforeToolExecution.request();
                            ToolRequestMessage toolRequestMessage = new ToolRequestMessage(request);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


}


package com.woopsion.woopsionaicodemother.service;

import com.mybatisflex.core.service.IService;
import com.woopsion.woopsionaicodemother.entity.ChatHistory;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);
}

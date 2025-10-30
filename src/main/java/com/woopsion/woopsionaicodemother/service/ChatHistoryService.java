package com.woopsion.woopsionaicodemother.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.woopsion.woopsionaicodemother.entity.ChatHistory;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.woopsion.woopsionaicodemother.model.vo.ChatHistoryVO;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {


    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
    /**
     * 添加对话消息
     *
     * @param appId 应用id
     * @param message 消息内容
     * @param messageType 消息类型
     * @param userId 用户id
     * @return 是否成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 获取查询条件
     *
     * @param chatHistoryQueryRequest 查询请求参数
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

//    /**
//     * 分页查询对话历史（游标分页，每次加载最新N条）
//     *
//     * @param chatHistoryQueryRequest 查询请求
//     * @return 对话历史列表和下一次游标
//     */
//    ChatHistoryPageResult listChatHistoryByCursor(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 获取对话历史封装
     *
     * @param chatHistory 对话历史
     * @return 对话历史封装
     */
    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    /**
     * 获取对话历史封装列表
     *
     * @param chatHistoryList 对话历史列表
     * @return 对话历史封装列表
     */
    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList);

    /**
     * 根据应用id删除所有对话历史（逻辑删除）
     *
     * @param appId 应用id
     * @return 删除的记录数
     */
    int deleteByAppId(Long appId);

    /**
     * 获取管理员查询条件
     *
     * @param chatHistoryAdminQueryRequest 查询请求参数
     * @return 查询条件
     */
    QueryWrapper getAdminQueryWrapper(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest);

    /**
     * 管理员分页查询对话历史
     *
     * @param chatHistoryAdminQueryRequest 查询请求
     * @return 分页结果
     */
    Page<ChatHistoryVO> listChatHistoryByPageAdmin(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

}

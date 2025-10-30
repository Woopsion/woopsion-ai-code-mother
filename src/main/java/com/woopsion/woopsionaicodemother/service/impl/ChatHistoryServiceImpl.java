package com.woopsion.woopsionaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.woopsion.woopsionaicodemother.constant.UserConstant;
import com.woopsion.woopsionaicodemother.entity.App;
import com.woopsion.woopsionaicodemother.entity.ChatHistory;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.exception.BusinessException;
import com.woopsion.woopsionaicodemother.exception.ErrorCode;
import com.woopsion.woopsionaicodemother.exception.ThrowUtils;
import com.mybatisflex.core.paginate.Page;
import com.woopsion.woopsionaicodemother.mapper.ChatHistoryMapper;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.woopsion.woopsionaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.woopsion.woopsionaicodemother.model.vo.ChatHistoryVO;
import com.woopsion.woopsionaicodemother.service.AppService;
import com.woopsion.woopsionaicodemother.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Autowired
    @Lazy
    AppService appService;

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }


    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }


//    @Override
//    public ChatHistoryPageResult listChatHistoryByCursor(ChatHistoryQueryRequest chatHistoryQueryRequest) {
//        if (chatHistoryQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        Long appId = chatHistoryQueryRequest.getAppId();
//        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
//
//        Integer pageSize = chatHistoryQueryRequest.getPageSize();
//        if (pageSize == null || pageSize <= 0) {
//            pageSize = 10; // 默认10条
//        }
//        // 限制每页最多50条
//        if (pageSize > 50) {
//            pageSize = 50;
//        }
//
//        // 构建查询条件
//        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryQueryRequest);
//        // 限制查询数量为 pageSize + 1，用于判断是否还有更多数据
//        queryWrapper.limit(pageSize + 1);
//
//        // 查询数据
//        List<ChatHistory> chatHistoryList = this.list(queryWrapper);
//
//        // 判断是否还有更多数据
//        boolean hasMore = chatHistoryList.size() > pageSize;
//        if (hasMore) {
//            // 如果有更多数据，移除最后一条
//            chatHistoryList = chatHistoryList.subList(0, pageSize);
//        }
//
//        // 转换为VO
//        List<ChatHistoryVO> chatHistoryVOList = getChatHistoryVOList(chatHistoryList);
//
//        // 计算下一次的游标（使用最小的时间作为游标）
//        LocalDateTime nextCursor = null;
//        if (hasMore && !chatHistoryVOList.isEmpty()) {
//            // 获取最后一条记录的时间作为下一次的游标
//            nextCursor = chatHistoryVOList.get(chatHistoryVOList.size() - 1).getCreateTime();
//        }
//
//        return new ChatHistoryPageResult(chatHistoryVOList, nextCursor, pageSize);
//    }

    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        if (chatHistory == null) {
            return null;
        }
        ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
        BeanUtil.copyProperties(chatHistory, chatHistoryVO);
        return chatHistoryVO;
    }

    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList) {
        if (CollUtil.isEmpty(chatHistoryList)) {
            return new ArrayList<>();
        }
        return chatHistoryList.stream().map(this::getChatHistoryVO).collect(Collectors.toList());
    }

    @Override
    public int deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        }
        // 逻辑删除：使用 MyBatis-Flex 的逻辑删除功能
        QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId);
        // 先查询要删除的记录数
        long count = this.count(queryWrapper);
        // 执行逻辑删除
        boolean result = this.remove(queryWrapper);
        return result ? (int) count : 0;
    }

    @Override
    public QueryWrapper getAdminQueryWrapper(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest) {
        if (chatHistoryAdminQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long appId = chatHistoryAdminQueryRequest.getAppId();
        String messageType = chatHistoryAdminQueryRequest.getMessageType();
        Long userId = chatHistoryAdminQueryRequest.getUserId();
        String sortField = chatHistoryAdminQueryRequest.getSortField();
        String sortOrder = chatHistoryAdminQueryRequest.getSortOrder();
        String message = chatHistoryAdminQueryRequest.getMessage();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .like("message",message)
                .eq("appId", appId)
                .eq("messageType", messageType)
                .eq("userId", userId);

        // 按创建时间降序排序（最新的在前面）
        queryWrapper.orderBy(sortField != null ? sortField : "createTime",
                "ascend".equals(sortOrder));

        return queryWrapper;
    }

    @Override
    public Page<ChatHistoryVO> listChatHistoryByPageAdmin(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest) {
        if (chatHistoryAdminQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        long pageNum = chatHistoryAdminQueryRequest.getPageNum();
        long pageSize = chatHistoryAdminQueryRequest.getPageSize();

        QueryWrapper queryWrapper = getAdminQueryWrapper(chatHistoryAdminQueryRequest);
        Page<ChatHistory> chatHistoryPage = this.page(Page.of(pageNum, pageSize), queryWrapper);

        // 转换为VO
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(pageNum, pageSize, chatHistoryPage.getTotalRow());
        List<ChatHistoryVO> chatHistoryVOList = getChatHistoryVOList(chatHistoryPage.getRecords());
        chatHistoryVOPage.setRecords(chatHistoryVOList);

        return chatHistoryVOPage;
    }
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        //这是 MyBatis-Plus 框架中的分页对象创建方法
        //第一个参数 1 表示当前页码（从1开始计数）
        //第二个参数 pageSize 表示每页显示的记录数
        //这里固定查询第1页，是因为配合了游标分页使用
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

}

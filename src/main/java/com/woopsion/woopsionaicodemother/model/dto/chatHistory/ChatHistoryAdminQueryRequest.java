package com.woopsion.woopsionaicodemother.model.dto.chatHistory;

import com.woopsion.woopsionaicodemother.common.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对话历史查询请求（管理员）
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatHistoryAdminQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 消息类型：user/ai
     */
    private String messageType;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 创建用户id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}


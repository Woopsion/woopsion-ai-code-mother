package com.woopsion.woopsionaicodemother.model.message;

/**
 * @author wangpengcan
 * @date 2025/10/30
 * @time 17:15
 * @description
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息响应基类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {
    private String type;
}


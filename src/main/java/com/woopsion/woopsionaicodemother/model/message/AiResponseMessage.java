package com.woopsion.woopsionaicodemother.model.message;

/**
 * @author wangpengcan
 * @date 2025/10/30
 * @time 17:16
 * @description
 */

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 响应消息
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AiResponseMessage extends StreamMessage {

    private String data;

    public AiResponseMessage(String data) {
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }
}


package com.woopsion.woopsionaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author wangpengcan
 * @date 2025/10/24
 * @time 17:35
 * @description
 */
@Data
public class UserLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}

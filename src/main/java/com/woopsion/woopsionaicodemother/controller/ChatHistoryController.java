package com.woopsion.woopsionaicodemother.controller;

import com.mybatisflex.core.paginate.Page;
import com.woopsion.woopsionaicodemother.annotation.AuthCheck;
import com.woopsion.woopsionaicodemother.common.BaseResponse;
import com.woopsion.woopsionaicodemother.common.ResultUtils;
import com.woopsion.woopsionaicodemother.constant.UserConstant;
import com.woopsion.woopsionaicodemother.entity.App;
import com.woopsion.woopsionaicodemother.entity.ChatHistory;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.exception.BusinessException;
import com.woopsion.woopsionaicodemother.exception.ErrorCode;
import com.woopsion.woopsionaicodemother.exception.ThrowUtils;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.woopsion.woopsionaicodemother.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.woopsion.woopsionaicodemother.model.vo.ChatHistoryVO;
import com.woopsion.woopsionaicodemother.service.AppService;
import com.woopsion.woopsionaicodemother.service.ChatHistoryService;
import com.woopsion.woopsionaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private UserService userService;

    // region 用户接口

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }


    // endregion

    // region 管理员接口

    /**
     * 管理员分页查询对话历史
     *
     * @param chatHistoryAdminQueryRequest 查询请求
     * @return 对话历史列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPageAdmin(
            @RequestBody ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest) {
        ThrowUtils.throwIf(chatHistoryAdminQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<ChatHistoryVO> result = chatHistoryService.listChatHistoryByPageAdmin(chatHistoryAdminQueryRequest);
        return ResultUtils.success(result);
    }

    // endregion
}

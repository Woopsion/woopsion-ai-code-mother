package com.woopsion.woopsionaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.woopsion.woopsionaicodemother.entity.App;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.model.dto.app.AppQueryRequest;
import com.woopsion.woopsionaicodemother.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public interface AppService extends IService<App> {

    /**
     * 聊天生成代码
     *
     * @param appId 应用id
     * @param message 聊天消息
     * @param loginUser 登录用户
     * @return 代码流式输出内容
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 校验应用参数
     *
     * @param app 应用
     * @param add 是否为创建
     */
    void validApp(App app, boolean add);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest 查询请求参数
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用封装
     *
     * @param app 应用
     * @return 应用封装
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装列表
     *
     * @param appList 应用列表
     * @return 应用封装列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 检查是否是应用的创建者
     *
     * @param app 应用
     * @param loginUser 登录用户
     * @return 是否是创建者
     */
    boolean isAppOwner(App app, User loginUser);
}

package com.woopsion.woopsionaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.model.dto.user.UserQueryRequest;
import com.woopsion.woopsionaicodemother.model.vo.LoginUserVO;
import com.woopsion.woopsionaicodemother.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public interface UserService extends IService<User> {
    String getEncryptPassword(String userPassword);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest)
        ;

    List<UserVO> getUserVOList(List<User> userList);

    UserVO getUserVO(User user);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);


    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);


    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

}

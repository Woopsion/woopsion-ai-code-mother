package com.woopsion.woopsionaicodemother.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.mapper.UserMapper;
import com.woopsion.woopsionaicodemother.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户 服务层实现。
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

}

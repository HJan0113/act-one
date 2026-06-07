package com.hjan.actone.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.user.dao.entity.UserDO;
import com.hjan.actone.user.dao.mapper.UserMapper;
import com.hjan.actone.user.model.LoginRequest;
import com.hjan.actone.user.model.LoginResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String DEFAULT_AVATAR_URL = "https://static.deepseek.com/user-avatar/G_6cuD8GbD53VwGRwisvCsZ6";

    private final UserMapper userMapper;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public LoginResponse login(LoginRequest request) {
        String username = trim(request.username());
        String password = trim(request.password());
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new ClientException("用户名或密码不能为空");
        }
        UserDO user = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeleted, 0));
        if (user == null || !passwordMatches(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        if (!StringUtils.hasText(user.getId())) {
            throw new ClientException("用户信息异常");
        }
        String avatar = StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : DEFAULT_AVATAR_URL;
        user.setAvatar(avatar);
        StpUtil.login(user.getId());
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole(), StpUtil.getTokenValue(), avatar);
    }

    public void logout() {
        StpUtil.logout();
    }

    private boolean passwordMatches(String input, String stored) {
        return stored != null && stored.equals(input);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

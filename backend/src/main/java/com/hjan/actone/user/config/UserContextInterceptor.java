package com.hjan.actone.user.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.user.dao.entity.UserDO;
import com.hjan.actone.user.dao.mapper.UserMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_AVATAR_URL = "https://static.deepseek.com/user-avatar/G_6cuD8GbD53VwGRwisvCsZ6";

    private final UserMapper userMapper;

    public UserContextInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String loginId = StpUtil.getLoginIdAsString();
        UserDO user = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, loginId)
                .eq(UserDO::getDeleted, 0));
        if (user == null) {
            StpUtil.logout();
            throw new ClientException("用户不存在或已被禁用");
        }
        UserContext.set(new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : DEFAULT_AVATAR_URL
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}

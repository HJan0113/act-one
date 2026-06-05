package com.hjan.actone.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.user.dao.entity.UserDO;
import com.hjan.actone.user.dao.mapper.UserMapper;
import com.hjan.actone.user.enums.UserRole;
import com.hjan.actone.user.model.ChangePasswordRequest;
import com.hjan.actone.user.model.UserCreateRequest;
import com.hjan.actone.user.model.UserPageRequest;
import com.hjan.actone.user.model.UserResponse;
import com.hjan.actone.user.model.UserUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public IPage<UserResponse> pageQuery(UserPageRequest request) {
        String keyword = trim(request.getKeyword());
        Page<UserDO> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<UserDO> result = userMapper.selectPage(
                page,
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getDeleted, 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(UserDO::getUsername, keyword)
                                .or()
                                .like(UserDO::getRole, keyword))
                        .orderByDesc(UserDO::getUpdateTime)
        );
        return result.convert(this::toResponse);
    }

    public String create(UserCreateRequest request) {
        String username = requireText(request.username(), "用户名不能为空");
        String password = requireText(request.password(), "密码不能为空");
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new ClientException("默认管理员用户名不可用");
        }
        ensureUsernameAvailable(username, null);

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(normalizeRole(request.role()));
        user.setAvatar(trim(request.avatar()));
        userMapper.insert(user);
        return user.getId();
    }

    public void update(String id, UserUpdateRequest request) {
        UserDO user = loadById(id);
        ensureNotDefaultAdmin(user);

        if (request.username() != null) {
            String username = requireText(request.username(), "用户名不能为空");
            if (!username.equals(user.getUsername())) {
                if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                    throw new ClientException("默认管理员用户名不可用");
                }
                ensureUsernameAvailable(username, user.getId());
            }
            user.setUsername(username);
        }
        if (request.password() != null) {
            user.setPassword(requireText(request.password(), "新密码不能为空"));
        }
        if (request.role() != null) {
            user.setRole(normalizeRole(request.role()));
        }
        if (request.avatar() != null) {
            user.setAvatar(trim(request.avatar()));
        }
        userMapper.updateById(user);
    }

    public void delete(String id) {
        UserDO user = loadById(id);
        ensureNotDefaultAdmin(user);
        userMapper.deleteById(user.getId());
    }

    public void changePassword(ChangePasswordRequest request) {
        String currentPassword = requireText(request.currentPassword(), "当前密码不能为空");
        String newPassword = requireText(request.newPassword(), "新密码不能为空");
        LoginUser loginUser = UserContext.requireUser();
        UserDO user = loadById(loginUser.userId());
        if (!currentPassword.equals(user.getPassword())) {
            throw new ClientException("当前密码不正确");
        }
        user.setPassword(newPassword);
        userMapper.updateById(user);
    }

    private UserDO loadById(String id) {
        String actualId = requireText(id, "用户 ID 不能为空");
        UserDO user = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, actualId)
                .eq(UserDO::getDeleted, 0));
        if (user == null) {
            throw new ClientException("用户不存在");
        }
        return user;
    }

    private void ensureUsernameAvailable(String username, String excludeId) {
        UserDO existing = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeleted, 0)
                .ne(StringUtils.hasText(excludeId), UserDO::getId, excludeId));
        if (existing != null) {
            throw new ClientException("用户名已存在");
        }
    }

    private void ensureNotDefaultAdmin(UserDO user) {
        if (user != null && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername())) {
            throw new ClientException("默认管理员不允许修改或删除");
        }
    }

    private String normalizeRole(String role) {
        String value = trim(role);
        if (!StringUtils.hasText(value)) {
            return UserRole.USER.code();
        }
        if (UserRole.ADMIN.code().equalsIgnoreCase(value)) {
            return UserRole.ADMIN.code();
        }
        if (UserRole.USER.code().equalsIgnoreCase(value)) {
            return UserRole.USER.code();
        }
        throw new ClientException("角色类型不合法");
    }

    private UserResponse toResponse(UserDO user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getAvatar(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    private String requireText(String value, String message) {
        String trimmed = trim(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new ClientException(message);
        }
        return trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}


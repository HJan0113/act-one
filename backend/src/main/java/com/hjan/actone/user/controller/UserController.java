package com.hjan.actone.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.user.enums.UserRole;
import com.hjan.actone.user.model.ChangePasswordRequest;
import com.hjan.actone.user.model.CurrentUserResponse;
import com.hjan.actone.user.model.UserCreateRequest;
import com.hjan.actone.user.model.UserPageRequest;
import com.hjan.actone.user.model.UserResponse;
import com.hjan.actone.user.model.UserUpdateRequest;
import com.hjan.actone.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/me")
    public Result<CurrentUserResponse> currentUser() {
        LoginUser user = UserContext.requireUser();
        return Results.success(new CurrentUserResponse(user.userId(), user.username(), user.role(), user.avatar()));
    }

    @GetMapping("/users")
    public Result<IPage<UserResponse>> pageQuery(UserPageRequest request) {
        StpUtil.checkRole(UserRole.ADMIN.code());
        return Results.success(userService.pageQuery(request));
    }

    @PostMapping("/users")
    public Result<String> create(@Valid @RequestBody UserCreateRequest request) {
        StpUtil.checkRole(UserRole.ADMIN.code());
        return Results.success(userService.create(request));
    }

    @PutMapping("/users/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest request) {
        StpUtil.checkRole(UserRole.ADMIN.code());
        userService.update(id, request);
        return Results.success();
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable String id) {
        StpUtil.checkRole(UserRole.ADMIN.code());
        userService.delete(id);
        return Results.success();
    }

    @PutMapping("/user/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Results.success();
    }
}

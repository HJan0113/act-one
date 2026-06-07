package com.hjan.actone.user.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hjan.actone.user.dao.entity.UserDO;
import com.hjan.actone.user.dao.mapper.UserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Component
public class SaTokenStpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    public SaTokenStpInterfaceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        UserDO user = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, loginId.toString())
                .eq(UserDO::getDeleted, 0));
        if (user == null || !StringUtils.hasText(user.getRole())) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}

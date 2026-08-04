package com.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.BizException;
import com.erp.dto.UserSaveRequest;
import com.erp.dto.UserUpdateRequest;
import com.erp.entity.SysColumnPref;
import com.erp.entity.SysUser;
import com.erp.mapper.SysColumnPrefMapper;
import com.erp.mapper.SysUserMapper;
import com.erp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理（仅 ADMIN）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysColumnPrefMapper columnPrefMapper;
    private final PasswordEncoder passwordEncoder;

    public Page<SysUser> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword));
        }
        wrapper.orderByDesc(SysUser::getId);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public void create(UserSaveRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw BizException.badRequest("密码不能为空");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername().trim()));
        if (count != null && count > 0) {
            throw BizException.badRequest("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRole(req.getRole() == null ? "USER" : req.getRole());
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(user);
    }

    public void update(Long id, UserUpdateRequest req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getRole() != null) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id) {
        if (id.equals(CurrentUser.id())) {
            throw BizException.badRequest("不能删除当前登录用户");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        userMapper.deleteById(id);
        columnPrefMapper.delete(new LambdaQueryWrapper<SysColumnPref>().eq(SysColumnPref::getUserId, id));
    }

    public List<SysUser> findByUsername(String username) {
        return userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }
}

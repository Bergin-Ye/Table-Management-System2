package com.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.entity.SysRoleMenu;
import com.erp.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色-菜单授权（仅 ADMIN 可调用，由 @PreAuthorize 控制）。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMenuMapper roleMenuMapper;

    public List<Long> getRoleMenus(String role) {
        List<SysRoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRole, role));
        return list.stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Transactional
    public void saveRoleMenus(String role, List<Long> menuIds) {
        roleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().eq("role", role));
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRole(role);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }
}

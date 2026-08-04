package com.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.BizException;
import com.erp.dto.MenuVO;
import com.erp.entity.SysMenu;
import com.erp.entity.SysRoleMenu;
import com.erp.mapper.SysMenuMapper;
import com.erp.mapper.SysRoleMenuMapper;
import com.erp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单树（按角色 RBAC 过滤）+ 单据类型访问鉴权。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /** 当前用户角色已授权的菜单 id 集合 */
    public Set<Long> authorizedMenuIds(String role) {
        List<SysRoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRole, role));
        Set<Long> ids = new HashSet<>();
        for (SysRoleMenu rm : list) {
            ids.add(rm.getMenuId());
        }
        return ids;
    }

    /** 当前用户可见菜单树 */
    public List<MenuVO> mine() {
        Set<Long> authorized = authorizedMenuIds(CurrentUser.role());
        List<SysMenu> all = menuMapper.selectList(null);
        List<SysMenu> visible = new ArrayList<>();
        for (SysMenu m : all) {
            if (authorized.contains(m.getId())) {
                visible.add(m);
            }
        }
        visible.sort(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo)));

        List<MenuVO> roots = new ArrayList<>();
        for (SysMenu m : visible) {
            if (m.getParentId() == null || m.getParentId() == 0) {
                roots.add(toVO(m));
            }
        }
        for (MenuVO root : roots) {
            root.setChildren(new ArrayList<>());
            for (SysMenu m : visible) {
                if (m.getParentId() != null && m.getParentId().equals(root.getId())) {
                    root.getChildren().add(toVO(m));
                }
            }
        }
        return roots;
    }

    /** 当前用户的角色菜单是否包含该 docType */
    public boolean hasDocTypePermission(String docType) {
        SysMenu menu = menuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getDocType, docType).last("LIMIT 1"));
        if (menu == null) {
            return false;
        }
        return authorizedMenuIds(CurrentUser.role()).contains(menu.getId());
    }

    /** 单据接口鉴权：无权限抛 403 */
    public void assertDocPermission(String docType) {
        if (!hasDocTypePermission(docType)) {
            throw BizException.forbidden("无权限访问该单据");
        }
    }

    private MenuVO toVO(SysMenu m) {
        MenuVO vo = new MenuVO();
        vo.setId(m.getId());
        vo.setName(m.getName());
        vo.setPath(m.getPath());
        vo.setDocType(m.getDocType());
        return vo;
    }
}

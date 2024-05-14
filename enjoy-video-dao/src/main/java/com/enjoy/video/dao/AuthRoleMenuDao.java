package com.enjoy.video.dao;

import com.enjoy.video.domain.auth.AuthRoleMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;

@Mapper
public interface AuthRoleMenuDao {

    List<AuthRoleMenu> getAuthRoleMenusByRoleIds(Set<Long> roleIdSet);
}

package com.enjoy.video.dao;

import com.enjoy.video.domain.UserMoment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMomentsDao {

    Integer addUserMoments(UserMoment userMoment);

    Integer pageCountMoments(Map<String, Object> params);

    List<UserMoment> pageListMoments(Map<String, Object> params);

}

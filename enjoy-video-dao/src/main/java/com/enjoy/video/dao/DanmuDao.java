package com.enjoy.video.dao;

import com.enjoy.video.domain.Danmu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface DanmuDao {

    Integer addDanmu(Danmu danmu);

    List<Danmu> getDanmus(Map<String,Object> params);
}

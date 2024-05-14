package com.enjoy.video.dao;

import com.enjoy.video.domain.Content;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContentDao {
    Long addContent(Content content);
}

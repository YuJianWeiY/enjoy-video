package com.enjoy.video.dao;

import com.enjoy.video.domain.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagDao {
    void addTag(Tag tag);

}

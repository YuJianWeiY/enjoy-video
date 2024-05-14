package com.enjoy.video.service;

import com.enjoy.video.dao.TagDao;
import com.enjoy.video.domain.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TagService {

    @Autowired
    private TagDao tagDao;

    public Long addTag(Tag tag) {
        tagDao.addTag(tag);
        return tag.getId();
    }
}

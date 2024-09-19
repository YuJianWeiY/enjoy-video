package com.enjoy.video.api;

import com.enjoy.video.domain.JsonResponse;
import com.enjoy.video.domain.Tag;
import com.enjoy.video.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TagApi {

    @Autowired
    private TagService tagService;

    @PostMapping("/tags")
    public JsonResponse<Long> addTag(@RequestBody Tag tag){
        Long tagId = tagService.addTag(tag);
        return new JsonResponse<>(tagId);
    }

}

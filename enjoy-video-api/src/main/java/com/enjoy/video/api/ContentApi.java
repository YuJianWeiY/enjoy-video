package com.enjoy.video.api;

import com.enjoy.video.domain.Content;
import com.enjoy.video.domain.JsonResponse;
import com.enjoy.video.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentApi {

    @Autowired
    private ContentService contentService;

    @PostMapping("/contents")
    public JsonResponse<Long> addContent(@RequestBody Content content){
        Long contentId = contentService.addContent(content);
        return new JsonResponse<>(contentId);
    }
}

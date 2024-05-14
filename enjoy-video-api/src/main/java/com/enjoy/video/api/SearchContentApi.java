package com.enjoy.video.api;

import com.enjoy.video.domain.JsonResponse;
import com.enjoy.video.domain.UserInfo;
import com.enjoy.video.domain.Video;
import com.enjoy.video.service.SearchContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SearchContentApi {

    @Autowired
    private SearchContentService searchContentService;


    @GetMapping("/search-counts")
    public JsonResponse<Map<String, Object>> countBySearchTxt(String searchTxt){
        Map<String, Object> result = searchContentService.countBySearchTxt(searchTxt);
        return new JsonResponse<>(result);
    }

    @GetMapping("/search-videos")
    public JsonResponse<Page<Video>> pageListSearchVideos(@RequestParam String keyword,
                                                          @RequestParam Integer pageSize,
                                                          @RequestParam Integer pageNo,
                                                          @RequestParam String searchType){
        Page<Video> result = searchContentService.pageListSearchVideos(keyword, pageSize,
                                                                        pageNo, searchType);
        return new JsonResponse<>(result);
    }

    @GetMapping("/search-users")
    public JsonResponse<Page<UserInfo>> pageListSearchUsers(@RequestParam String keyword,
                                                            @RequestParam Integer pageSize,
                                                            @RequestParam Integer pageNo,
                                                            @RequestParam String searchType){
        Page<UserInfo> result = searchContentService.pageListSearchUsers(keyword, pageSize,
                pageNo, searchType);
        return new JsonResponse<>(result);
    }

}

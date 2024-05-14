package com.enjoy.video.service;

import com.enjoy.video.domain.UserInfo;
import com.enjoy.video.domain.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SearchContentService {

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Value("${fdfs.http.storage-addr}")
    private String fastdfsUrl;

    public Map<String, Object> countBySearchTxt(String searchTxt) {
        Map<String, Object> result = new HashMap<>();
        //算视频
        long videoCount = elasticSearchService.countVideoBySearchTxt(searchTxt);
        //算用户
        long userCount = elasticSearchService.countUserBySearchTxt(searchTxt);
        //构建返回结果
        result.put("videoCount", videoCount);
        result.put("userCount", userCount);
        return result;
    }

    public Page<Video> pageListSearchVideos(String keyword,Integer pageSize,
                                            Integer pageNo, String searchType) {
        Page<Video> result = elasticSearchService.pageListSearchVideos(keyword, pageSize,
                                                                pageNo-1, searchType);
        result.getContent().forEach(item  -> item.setThumbnail(fastdfsUrl + item.getThumbnail()));
        return result;
    }

    public Page<UserInfo> pageListSearchUsers(String keyword, Integer pageSize,
                                              Integer pageNo, String searchType) {
        Page<UserInfo> result = elasticSearchService.pageListSearchUsers(keyword, pageSize,
                                                                pageNo-1, searchType);
        return result;
    }
}

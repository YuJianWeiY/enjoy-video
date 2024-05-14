package com.enjoy.video.api;

import com.enjoy.video.domain.JsonResponse;
import com.enjoy.video.domain.UserInfo;
import com.enjoy.video.domain.Video;
import com.enjoy.video.service.DemoService;
import com.enjoy.video.service.ElasticSearchService;
import com.enjoy.video.service.feign.MsDeclareService;
import com.enjoy.video.service.util.FastDFSUtil;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class DemoApi {

    @Autowired
    private DemoService demoService;

    @Autowired
    private FastDFSUtil fastDFSUtil;

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Autowired
    private MsDeclareService msDeclareService;

    @GetMapping("/query")
    public Long query(Long id){
        return demoService.query(id);
    }

    @GetMapping("/slices")
    public void slices(MultipartFile file) throws Exception {
        fastDFSUtil.convertFileToSlices(file);
    }

    @GetMapping("/es-videos")
    public JsonResponse<Video> getEsVideos(@RequestParam String keyword){
       Video video = elasticSearchService.getVideos(keyword);
       return new JsonResponse<>(video);
    }

    @PostMapping("/es-videos")
    public JsonResponse<String> addVideos(@RequestBody Video video){
        elasticSearchService.addVideo(video);
        return JsonResponse.success();
    }

    @DeleteMapping("/es-videos")
    public JsonResponse<String> deleteVideos(){
        elasticSearchService.deleteAllVideos();
        return JsonResponse.success();
    }

    @PostMapping("/es-users")
    public JsonResponse<String> addUsers(@RequestBody UserInfo userInfo){
        elasticSearchService.addUserInfo(userInfo);
        return JsonResponse.success();
    }

    @GetMapping("/demos")
    public Long msget(@RequestParam Long id){
        return msDeclareService.msget(id);
    }

    @PostMapping("/demos")
    public Map<String, Object> mspost(@RequestBody Map<String, Object> params){
        return msDeclareService.mspost(params);
    }

    @HystrixCommand(fallbackMethod = "error",
        commandProperties = {
            @HystrixProperty(
                name = "execution.isolation.thread.timeoutInMilliseconds",
                value = "2000"
            )
        }
    )
    @GetMapping("/timeout")
    public String circuitBreakerWithHystrix(@RequestParam Long time){
        return msDeclareService.timeout(time);
    }

    public String error(Long time){
        return "超时出错！";
    }

}

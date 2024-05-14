package com.enjoy.video.service;

import com.alibaba.fastjson.JSONObject;
import com.enjoy.video.dao.VideoDao;
import com.enjoy.video.domain.*;
import com.enjoy.video.domain.constant.UserMomentsConstant;
import com.enjoy.video.domain.exception.ConditionException;
import com.enjoy.video.service.util.FastDFSUtil;
import com.enjoy.video.service.util.ImageUtil;
import com.enjoy.video.service.util.IpUtil;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {

    @Autowired
    private VideoDao videoDao;

    @Autowired
    private FastDFSUtil fastDFSUtil;

    @Autowired
    private UserCoinService userCoinService;

    @Autowired
    private UserService userService;

    @Autowired
    private ImageUtil imageUtil;

    @Autowired
    private FileService fileService;

    @Autowired
    private ContentService contentService;

    @Autowired
    private UserMomentsService userMomentsService;

    private static final int DEFAULT_RECOMMEND_NUMBER = 3;

    private static final int FRAME_NO = 256;

    @Value("${fdfs.http.storage-addr}")
    private String fastdfsUrl;

    @Transactional
    public void addVideos(Video video) {
        Date now = new Date();
        video.setCreateTime(new Date());
        videoDao.addVideos(video);
        //保存视频标签
        Long videoId = video.getId();
        List<VideoTag> tagList = video.getVideoTagList();
        tagList.forEach(item -> {
            item.setCreateTime(now);
            item.setVideoId(videoId);
        });
        videoDao.batchAddVideoTags(tagList);
        videoDao.batchAddVideoTags(tagList);
        //新增：自动发布动态
        try{
            //添加动态内容
            Content content = new Content();
            content.setContentDetail(JSONObject.parseObject(JSONObject.toJSONString(video)));
            contentService.addContent(content);
            Long contentId = content.getId();
            //添加用户发布视频动态
            UserMoment moment = new UserMoment();
            moment.setType(UserMomentsConstant.TYPE_VIDEO);
            moment.setContentId(contentId);
            moment.setUserId(video.getUserId());
            userMomentsService.addUserMoments(moment);
        }catch (Exception e){
            throw new ConditionException("发布视频动态失败");
        }
    }

    public PageResult<Video> pageListVideos(Integer size, Integer no, String area) {
        Map<String, Object> params = new HashMap<>();
        params.put("start", (no-1)*size);
        params.put("limit", size);
        params.put("area" , area);
        List<Video> list = new ArrayList<>();
        Integer total = videoDao.pageCountVideos(params);
        if(total > 0){
            list = videoDao.pageListVideos(params);
            //视频封面相对路径转为绝对路径
            list.forEach(video -> video.setThumbnail(fastdfsUrl + video.getThumbnail()));
            //统计播放量和弹幕量
            list = this.getVideoCount(list);
        }
        return new PageResult<>(total, list);
    }

    public List<Video> getVideoCount(List<Video> videoList){
        if(!videoList.isEmpty()){
            //获取视频id集合
            Set<Long> videoIdSet = videoList.stream().map(Video :: getId)
                    .collect(Collectors.toSet());
            //统计播放量
            Map<Long, Integer> viewCountMap = this.batchCountVideoView(videoIdSet);
            //统计弹幕量
            Map<Long, Integer> danmuCountMap = this.batchCountVideoDanmu(videoIdSet);
            //构建返回数据
            videoList.forEach(video -> {
                video.setViewCount(viewCountMap.get(video.getId()));
                video.setDanmuCount(danmuCountMap.get(video.getId()));
            });
        }
        return videoList;
    }

    //统计视频播放量
    public Map<Long, Integer>  batchCountVideoView(Set<Long> videoIdSet){
        List<VideoViewCount> viewCount = videoDao.getVideoViewCountByVideoIds(videoIdSet);
        return viewCount.stream()
                .collect(Collectors.toMap(VideoViewCount::getVideoId,
                        VideoViewCount::getCount));
    }

    //统计视频弹幕量
    public Map<Long, Integer> batchCountVideoDanmu(Set<Long> videoIdSet){
        List<VideoDanmuCount> danmuCount = videoDao.getVideoDanmuCountByVideoIds(videoIdSet);
        return danmuCount.stream()
                .collect(Collectors.toMap(VideoDanmuCount::getVideoId,
                        VideoDanmuCount::getCount));
    }

    public void viewVideoOnlineBySlices(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String url) {
        try{
            fastDFSUtil.viewVideoOnlineBySlices(request, response, url);
        }catch (Exception ignored){}
    }

    public void addVideoLike(Long videoId, Long userId) {
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        VideoLike videoLike = videoDao.getVideoLikeByVideoIdAndUserId(videoId, userId);
        if(videoLike != null){
            throw new ConditionException("已经赞过！");
        }
        videoLike = new VideoLike();
        videoLike.setVideoId(videoId);
        videoLike.setUserId(userId);
        videoLike.setCreateTime(new Date());
        videoDao.addVideoLike(videoLike);
    }

    public void deleteVideoLike(Long videoId, Long userId) {
        videoDao.deleteVideoLike(videoId, userId);
    }

    public Map<String, Object> getVideoLikes(Long videoId, Long userId) {
        Long count = videoDao.getVideoLikes(videoId);
        VideoLike videoLike = videoDao.getVideoLikeByVideoIdAndUserId(videoId, userId);
        boolean like = videoLike != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("like", like);
        return result;
    }


    @Transactional
    public void addVideoCollection(VideoCollection videoCollection, Long userId) {
        Long videoId = videoCollection.getVideoId();
        Long groupId = videoCollection.getGroupId();
        if(videoId == null || groupId == null){
            throw new ConditionException("参数异常！");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        //删除原有视频收藏
        videoDao.deleteVideoCollection(videoId, userId);
        //添加新的视频收藏
        videoCollection.setUserId(userId);
        videoCollection.setCreateTime(new Date());
        videoDao.addVideoCollection(videoCollection);
    }

    @Transactional
    public void updateVideoCollection(VideoCollection videoCollection, Long userId) {
        Long videoId = videoCollection.getVideoId();
        Long groupId = videoCollection.getGroupId();
        if(videoId == null || groupId == null){
            throw new ConditionException("参数异常！");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        videoCollection.setUserId(userId);
        videoDao.updateVideoCollection(videoCollection);
    }

    public void deleteVideoCollection(Long videoId, Long userId) {
        videoDao.deleteVideoCollection(videoId, userId);
    }

    public Map<String, Object> getVideoCollections(Long videoId, Long userId) {
        Long count = videoDao.getVideoCollections(videoId);
        VideoCollection videoCollection = videoDao.getVideoCollectionByVideoIdAndUserId(videoId, userId);
        boolean like = videoCollection != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("like", like);
        return result;
    }


    @Transactional
    public void addVideoCoins(VideoCoin videoCoin, Long userId) {
        Long videoId = videoCoin.getVideoId();
        Integer amount = videoCoin.getAmount();
        if(videoId == null){
            throw new ConditionException("参数异常！");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        //查询当前登录用户是否拥有足够的硬币
        Integer userCoinsAmount = userCoinService.getUserCoinsAmount(userId);
        userCoinsAmount = userCoinsAmount == null ? 0 : userCoinsAmount;
        if(amount > userCoinsAmount){
            throw new ConditionException("硬币数量不足！");
        }
        //查询当前登录用户对该视频已经投了多少硬币
        VideoCoin dbVideoCoin = videoDao.getVideoCoinByVideoIdAndUserId(videoId, userId);
        //新增视频投币
        if(dbVideoCoin == null){
            videoCoin.setUserId(userId);
            videoCoin.setCreateTime(new Date());
            videoDao.addVideoCoin(videoCoin);
        }else{
            Integer dbAmount = dbVideoCoin.getAmount();
            dbAmount += amount;
            //更新视频投币
            videoCoin.setUserId(userId);
            videoCoin.setAmount(dbAmount);
            videoCoin.setUpdateTime(new Date());
            videoDao.updateVideoCoin(videoCoin);
        }
        //更新用户当前硬币总数
        userCoinService.updateUserCoinsAmount(userId, (userCoinsAmount-amount));
    }

    public Map<String, Object> getVideoCoins(Long videoId, Long userId) {
        Long count = videoDao.getVideoCoinsAmount(videoId);
        VideoCoin videoCollection = videoDao.getVideoCoinByVideoIdAndUserId(videoId, userId);
        boolean like = videoCollection != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("like", like);
        return result;
    }

    public void addVideoComment(VideoComment videoComment, Long userId) {
        Long videoId = videoComment.getVideoId();
        if(videoId == null){
            throw new ConditionException("参数异常！");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        videoComment.setUserId(userId);
        videoComment.setCreateTime(new Date());
        videoDao.addVideoComment(videoComment);
    }

    public PageResult<VideoComment> pageListVideoComments(Integer size, Integer no, Long videoId) {
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("非法视频！");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("start", (no-1)*size);
        params.put("limit", size);
        params.put("videoId", videoId);
        Integer total = videoDao.pageCountVideoComments(params);
        List<VideoComment> list = new ArrayList<>();
        if(total > 0){
            list = videoDao.pageListVideoComments(params);
            if(!list.isEmpty()){
                //批量查询二级评论
                List<Long> parentIdList = list.stream().map(VideoComment::getId).collect(Collectors.toList());
                //批量查询用户信息
                Set<Long> userIdList = list.stream().map(VideoComment::getUserId).collect(Collectors.toSet());
                List<VideoComment> childCommentList = videoDao.batchGetVideoCommentsByRootIds(parentIdList);
                Set<Long> replyUserIdList = childCommentList.stream()
                        .map(VideoComment::getUserId).collect(Collectors.toSet());
                Set<Long> childUserIdList = childCommentList.stream()
                        .map(VideoComment::getReplyUserId).collect(Collectors.toSet());
                userIdList.addAll(replyUserIdList);
                userIdList.addAll(childUserIdList);
                List<UserInfo> userInfoList = userService.batchGetUserInfoByUserIds(userIdList);
                Map<Long, UserInfo> userInfoMap = userInfoList.stream()
                        .collect(Collectors.toMap(UserInfo :: getUserId, userInfo -> userInfo));
                list.forEach(comment -> {
                    Long id = comment.getId();
                    List<VideoComment> childList = new ArrayList<>();
                    childCommentList.forEach(child -> {
                        if(id.equals(child.getRootId())){
                            child.setUserInfo(userInfoMap.get(child.getUserId()));
                            child.setReplyUserInfo(userInfoMap.get(child.getReplyUserId()));
                            childList.add(child);
                        }
                    });
                    comment.setChildList(childList);
                    comment.setUserInfo(userInfoMap.get(comment.getUserId()));
                });
            }
        }
        return new PageResult<>(total, list);
    }

    public Map<String, Object> getVideoDetails(Long videoId) {
        Video video =  videoDao.getVideoDetails(videoId);
        Long userId = video.getUserId();
        User user = userService.getUserInfo(userId);
        UserInfo userInfo = user.getUserInfo();
        Map<String, Object> result = new HashMap<>();
        result.put("video", video);
        result.put("userInfo", userInfo);
        return result;
    }

    public void addVideoView(VideoView videoView, HttpServletRequest request) {
        Long userId = videoView.getUserId();
        Long videoId = videoView.getVideoId();
        //生成clientId
        String agent = request.getHeader("User-Agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(agent);
        String clientId = String.valueOf(userAgent.getId());
        String ip = IpUtil.getIP(request);
        Map<String, Object> params = new HashMap<>();
        if(userId != null){
            params.put("userId", userId);
        }else{
            params.put("ip", ip);
            params.put("clientId", clientId);
        }
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        params.put("today", sdf.format(now));
        params.put("videoId", videoId);
        //添加观看记录
        VideoView dbVideoView = videoDao.getVideoView(params);
        if(dbVideoView == null){
            videoView.setIp(ip);
            videoView.setClientId(clientId);
            videoView.setCreateTime(new Date());
            videoDao.addVideoView(videoView);
        }
    }

    public Integer getVideoViewCounts(Long videoId) {
        return videoDao.getVideoViewCounts(videoId);
    }

    /**
     * 基于用户的协同推荐
     * @param userId 用户id
     */
    public List<Video> recommend(Long userId) throws TasteException {
        List<UserPreference> list = videoDao.getAllUserPreference();
        //创建数据模型
        DataModel dataModel = this.createDataModel(list);
        //获取用户相似程度
        UserSimilarity similarity = new UncenteredCosineSimilarity(dataModel);
        System.out.println(similarity.userSimilarity(11, 12));
        //获取用户邻居
        UserNeighborhood userNeighborhood = new NearestNUserNeighborhood(2, similarity, dataModel);
        long[] ar = userNeighborhood.getUserNeighborhood(userId);
        //构建推荐器
        Recommender recommender = new GenericUserBasedRecommender(dataModel, userNeighborhood, similarity);
        //推荐视频
        List<RecommendedItem> recommendedItems = recommender.recommend(userId, 5);
        List<Long> itemIds = recommendedItems.stream().map(RecommendedItem::getItemID).collect(Collectors.toList());
        return videoDao.batchGetVideosByIds(itemIds);
    }

    /**
     * 基于内容的协同推荐
     * @param userId 用户id
     * @param itemId 参考内容id（根据该内容进行相似内容推荐）
     * @param howMany 需要推荐的数量
     */
    public List<Video> recommendByItem(Long userId, Long itemId, int howMany) throws TasteException {
        List<UserPreference> list = videoDao.getAllUserPreference();
        //创建数据模型
        DataModel dataModel = this.createDataModel(list);
        //获取内容相似程度
        ItemSimilarity similarity = new UncenteredCosineSimilarity(dataModel);
        GenericItemBasedRecommender genericItemBasedRecommender = new GenericItemBasedRecommender(dataModel, similarity);
        // 物品推荐相拟度，计算两个物品同时出现的次数，次数越多任务的相拟度越高
        List<Long> itemIds = genericItemBasedRecommender.recommendedBecause(userId, itemId, howMany)
                .stream()
                .map(RecommendedItem::getItemID)
                .collect(Collectors.toList());
        //推荐视频
        return videoDao.batchGetVideosByIds(itemIds);
    }

    private DataModel createDataModel(List<UserPreference> userPreferenceList) {
        FastByIDMap<PreferenceArray> fastByIdMap = new FastByIDMap<>();
        Map<Long, List<UserPreference>> map = userPreferenceList.stream().collect(Collectors.groupingBy(UserPreference::getUserId));
        Collection<List<UserPreference>> list = map.values();
        for(List<UserPreference> userPreferences : list){
            GenericPreference[] array = new GenericPreference[userPreferences.size()];
            for(int i = 0; i < userPreferences.size(); i++){
                UserPreference userPreference = userPreferences.get(i);
                GenericPreference item = new GenericPreference(userPreference.getUserId(), userPreference.getVideoId(), userPreference.getValue());
                array[i] = item;
            }
            fastByIdMap.put(array[0].getUserID(), new GenericUserPreferenceArray(Arrays.asList(array)));
        }
        return new GenericDataModel(fastByIdMap);
    }

    public List<VideoBinaryPicture> convertVideoToImage(Long videoId, String fileMd5) throws Exception{
        com.enjoy.video.domain.File file = fileService.getFileByMd5(fileMd5);
        String filePath = "C:\\Users\\Lenovo\\Desktop\\file\\file\\file" + videoId + "." + file.getType();
        fastDFSUtil.downLoadFile(file.getUrl(), filePath);
        FFmpegFrameGrabber fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(filePath);
        fFmpegFrameGrabber.start();
        int ffLength = fFmpegFrameGrabber.getLengthInFrames();
        Frame frame;
        Java2DFrameConverter converter = new Java2DFrameConverter();
        int count = 1;
        List<VideoBinaryPicture> pictures = new ArrayList<>();
        for(int i=1; i<= ffLength; i ++){
            long timestamp = fFmpegFrameGrabber.getTimestamp();
            frame = fFmpegFrameGrabber.grabImage();
            if(count == i){
                if(frame == null){
                    throw new ConditionException("无效帧");
                }
                BufferedImage bufferedImage = converter.getBufferedImage(frame);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", os);
                InputStream inputStream = new ByteArrayInputStream(os.toByteArray());
                //输出黑白剪影文件
                File outputFile = File.createTempFile("convert-" + videoId + "-", ".png");
                BufferedImage binaryImg = imageUtil.getBodyOutline(bufferedImage, inputStream);
                ImageIO.write(binaryImg, "png", outputFile);
                //有的浏览器或网站需要把图片白色的部分转为透明色，使用以下方法可实现
                imageUtil.transferAlpha(outputFile, outputFile);
                //上传视频剪影文件
                String imgUrl = fastDFSUtil.uploadCommonFile(outputFile, "png");
                VideoBinaryPicture videoBinaryPicture = new VideoBinaryPicture();
                videoBinaryPicture.setFrameNo(i);
                videoBinaryPicture.setUrl(imgUrl);
                videoBinaryPicture.setVideoId(videoId);
                videoBinaryPicture.setVideoTimestamp(timestamp);
                pictures.add(videoBinaryPicture);
                count += FRAME_NO;
                //删除临时文件
                outputFile.delete();
            }
        }
        //删除临时文件
        File tmpFile = new File(filePath);
        tmpFile.delete();
        //批量添加视频剪影文件
        videoDao.batchAddVideoBinaryPictures(pictures);
        return pictures;
    }

    public List<VideoTag> getVideoTagsByVideoId(Long videoId) {
        return videoDao.getVideoTagsByVideoId(videoId);
    }

    public void deleteVideoTags(List<Long> tagIdList, Long videoId) {
        videoDao.deleteVideoTags(tagIdList, videoId);
    }

    public List<VideoBinaryPicture> getVideoBinaryImages(Map<String, Object> params) {
        return videoDao.getVideoBinaryImages(params);
    }

    public List<Video> getVideoRecommendations(String recommendType, Long userId){
        List<Video> list = new ArrayList<>();
        try {
            //根据推荐类型进行推荐：1基于用户推荐 2基于内容推荐
            if("1".equals(recommendType)){
                list = this.recommend(userId);
            }else{
                //找到用户最喜欢的视频，作为推荐的基础内容
                List<UserPreference> preferencesList = videoDao.getAllUserPreference();
                Optional<Long> itemIdOpt = preferencesList.stream().filter(item -> item.getUserId().equals(userId))
                        .max(Comparator.comparing(UserPreference :: getValue)).map(UserPreference::getVideoId);
                if(itemIdOpt.isPresent()){
                    list = this.recommendByItem(userId, itemIdOpt.get(), DEFAULT_RECOMMEND_NUMBER);
                }
            }
            //若没有计算出推荐内容，则默认查询最新视频
            if(list.isEmpty()){
                list = this.pageListVideos(3,1,null).getList();
            }else{
                list.forEach(video -> video.setThumbnail(fastdfsUrl+video.getThumbnail()));
            }
        }catch (Exception e){
            throw new ConditionException("推荐失败");
        }
        return list;
    }

    public List<Video> getVisitorVideoRecommendations() {
        return this.pageListVideos(DEFAULT_RECOMMEND_NUMBER,1,null).getList();
    }
}

package com.enjoy.video.dao.repository;

import com.enjoy.video.domain.UserInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserInfoRepository extends ElasticsearchRepository<UserInfo, Long> {

    long countByNick(String nickKeyword);

    Page<UserInfo> findByNickOrderByFanCountDesc(String nickKeyword, PageRequest pageRequest);

    Page<UserInfo> findByNickOrderByFanCountAsc(String nickKeyword, PageRequest pageRequest);
}

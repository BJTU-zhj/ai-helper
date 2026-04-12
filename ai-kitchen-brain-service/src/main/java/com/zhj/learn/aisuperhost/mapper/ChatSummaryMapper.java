package com.zhj.learn.aisuperhost.mapper;

import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.domain.ChatSummaryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatSummaryMapper {
    long countByExample(ChatSummaryExample example);

    int deleteByExample(ChatSummaryExample example);

    int deleteByPrimaryKey(String sessionId);

    int insert(ChatSummary row);

    int insertSelective(ChatSummary row);

    List<ChatSummary> selectByExampleWithBLOBs(ChatSummaryExample example);

    List<ChatSummary> selectByExample(ChatSummaryExample example);

    ChatSummary selectByPrimaryKey(String sessionId);

    int updateByExampleSelective(@Param("row") ChatSummary row, @Param("example") ChatSummaryExample example);

    int updateByExampleWithBLOBs(@Param("row") ChatSummary row, @Param("example") ChatSummaryExample example);

    int updateByExample(@Param("row") ChatSummary row, @Param("example") ChatSummaryExample example);

    int updateByPrimaryKeySelective(ChatSummary row);

    int updateByPrimaryKeyWithBLOBs(ChatSummary row);

    int updateByPrimaryKey(ChatSummary row);
}
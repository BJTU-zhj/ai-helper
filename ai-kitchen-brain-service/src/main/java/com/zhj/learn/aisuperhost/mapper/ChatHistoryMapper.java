package com.zhj.learn.aisuperhost.mapper;

import com.zhj.learn.aisuperhost.domain.ChatHistory;
import com.zhj.learn.aisuperhost.domain.ChatHistoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatHistoryMapper {
    long countByExample(ChatHistoryExample example);

    int deleteByExample(ChatHistoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ChatHistory row);

    int insertSelective(ChatHistory row);

    List<ChatHistory> selectByExampleWithBLOBs(ChatHistoryExample example);

    List<ChatHistory> selectByExample(ChatHistoryExample example);

    ChatHistory selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ChatHistory row, @Param("example") ChatHistoryExample example);

    int updateByExampleWithBLOBs(@Param("row") ChatHistory row, @Param("example") ChatHistoryExample example);

    int updateByExample(@Param("row") ChatHistory row, @Param("example") ChatHistoryExample example);

    int updateByPrimaryKeySelective(ChatHistory row);

    int updateByPrimaryKeyWithBLOBs(ChatHistory row);

    int updateByPrimaryKey(ChatHistory row);
}
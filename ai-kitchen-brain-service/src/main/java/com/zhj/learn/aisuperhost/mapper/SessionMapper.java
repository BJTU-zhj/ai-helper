package com.zhj.learn.aisuperhost.mapper;

import com.zhj.learn.aisuperhost.domain.Session;
import com.zhj.learn.aisuperhost.domain.SessionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SessionMapper {
    long countByExample(SessionExample example);

    int deleteByExample(SessionExample example);

    int deleteByPrimaryKey(String id);

    int insert(Session row);

    int insertSelective(Session row);

    List<Session> selectByExample(SessionExample example);

    Session selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") Session row, @Param("example") SessionExample example);

    int updateByExample(@Param("row") Session row, @Param("example") SessionExample example);

    int updateByPrimaryKeySelective(Session row);

    int updateByPrimaryKey(Session row);
}
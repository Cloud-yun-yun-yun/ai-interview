package com.yun.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.yun.aiinterview.entity.InterviewQuestion;
import com.yun.aiinterview.pojo.vo.InterviewQuestionVO;

import java.util.List;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {

    /**
     * 批量插入面试题目，
     * userAnswer 留待后续接口填充。
     */
    int insertList(@Param("list") List<InterviewQuestion> questions);
}
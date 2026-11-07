package com.yun.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.yun.aiinterview.entity.KnowledgeBaseQuestion;

import java.util.List;

@Mapper
public interface KnowledgeBaseQuestionMapper extends BaseMapper<KnowledgeBaseQuestion> {

    int saveList(@Param("list") List<KnowledgeBaseQuestion> list);
}
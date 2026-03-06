package org.example.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.document.entity.Document;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentMapper extends BaseMapper<Document> {
}


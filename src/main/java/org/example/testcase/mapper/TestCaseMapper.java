package org.example.testcase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.testcase.entity.TestCase;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseMapper extends BaseMapper<TestCase> {
}


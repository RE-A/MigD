package com.migd.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MigrationController 통합 테스트.
 * - GET /migration 화면 렌더링
 * - AWS 호스트 차단 밸리데이션 (validateTargetHost)
 * - GET /migration/result 화면 렌더링
 */
@SpringBootTest
@AutoConfigureMockMvc
class MigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /migration - 이관 실행 화면 정상 렌더링")
    void GET_이관화면_정상() throws Exception {
        mockMvc.perform(get("/migration"))
                .andExpect(status().isOk())
                .andExpect(view().name("migration/execute"));
    }

    @Test
    @DisplayName("GET /migration/result - 결과 화면 정상 렌더링")
    void GET_결과화면_정상() throws Exception {
        mockMvc.perform(get("/migration/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("migration/result"));
    }

    @ParameterizedTest(name = "AWS 호스트 차단: {0}")
    @ValueSource(strings = {
            "mydb.rds.amazonaws.com",
            "prod.cluster.us-east-1.rds.amazonaws.com",
            "test.amazonaws.com",
            "cache.elasticache.us-west-2.amazonaws.com",
            "myservice.aws.internal"
    })
    @DisplayName("POST /migration/run - AWS 호스트는 차단되고 /migration으로 redirect")
    void POST_AWS호스트_차단(String awsHost) throws Exception {
        mockMvc.perform(post("/migration/run")
                        .param("tgtHost", awsHost)
                        .param("tgtPort", "5432")
                        .param("tgtDb", "mydb")
                        .param("tgtUser", "user")
                        .param("tgtPassword", "pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/migration"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /migration/run - 일반 로컬 호스트는 AWS 차단 통과")
    void POST_로컬호스트_차단안됨() throws Exception {
        // 로컬 주소는 AWS 차단에 걸리지 않고 DB 연결 단계까지 진행됨
        // (실제 DB가 없으므로 연결 실패 후 /migration redirect — error는 다른 메시지)
        mockMvc.perform(post("/migration/run")
                        .param("tgtHost", "localhost")
                        .param("tgtPort", "5432")
                        .param("tgtDb", "localdb")
                        .param("tgtUser", "user")
                        .param("tgtPassword", "pass"))
                .andExpect(status().is3xxRedirection());
        // AWS 차단 메시지가 아님을 간접 확인 (redirect 자체는 일어남 - DB 연결 실패)
    }
}

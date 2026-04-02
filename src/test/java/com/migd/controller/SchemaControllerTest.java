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
 * SchemaController 통합 테스트.
 * - GET /schema 화면 렌더링
 * - AWS 호스트 차단 밸리데이션
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /schema - 스키마 이관 화면 정상 렌더링")
    void GET_스키마화면_정상() throws Exception {
        mockMvc.perform(get("/schema"))
                .andExpect(status().isOk())
                .andExpect(view().name("schema/execute"));
    }

    @ParameterizedTest(name = "AWS 호스트 차단: {0}")
    @ValueSource(strings = {
            "prod.rds.amazonaws.com",
            "data.us-east-1.rds.amazonaws.com",
            "something.amazonaws.com"
    })
    @DisplayName("POST /schema/run - AWS 호스트는 차단되고 /schema로 redirect")
    void POST_AWS호스트_차단(String awsHost) throws Exception {
        mockMvc.perform(post("/schema/run")
                        .param("tgtHost", awsHost)
                        .param("tgtPort", "5432")
                        .param("tgtDb", "mydb")
                        .param("tgtUser", "user")
                        .param("tgtPassword", "pass")
                        .param("schemaName", "public"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/schema"))
                .andExpect(flash().attributeExists("error"));
    }
}

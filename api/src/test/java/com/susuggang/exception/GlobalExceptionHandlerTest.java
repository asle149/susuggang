package com.susuggang.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 스프링 전체 기동 없이 미니 컨트롤러 + 어드바이스만 붙여 예외 → 공통 포맷 변환을 검증
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("내부 구현 상세가 담긴 메시지");
        }

        @GetMapping("/business")
        String business() {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 미분류_예외도_공통_포맷으로_나간다() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SSG10006"))
                .andExpect(jsonPath("$.msg").value("일시적인 오류가 발생했습니다"));
    }

    @Test
    void 구체_타입_핸들러가_최종_핸들러보다_우선한다() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_NOT_FOUND.getCode()));
    }
}

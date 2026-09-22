package com.rizenfood.api.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.member.PhoneCipher;

/**
 * 비회원 주문 조회.
 *
 * 결제 후 받은 링크를 잃은 손님이 스스로 찾을 수 있게 하는 통로인데,
 * 잘못 만들면 <b>남의 주문을 들여다보는 통로</b>가 된다. 그래서 규칙을 테스트로 고정한다.
 *
 *   - 주문번호만으로는 열리지 않는다 (연락처가 함께 맞아야 한다)
 *   - 회원 주문은 이 경로로 열리지 않는다 (로그인해서 봐야 한다)
 *   - 틀렸을 때와 없을 때의 응답이 같다 (주문번호의 존재 여부를 알려주지 않는다)
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GuestOrderLookupTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PhoneCipher cipher;

    private static final String PHONE = "01012345678";

    @Test
    @DisplayName("주문번호와 받는 분 연락처가 맞으면 주문을 보여준다")
    void opensWithOrderNoAndPhone() throws Exception {
        String orderNo = insertGuestOrder(PHONE);

        mvc.perform(lookup(orderNo, "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.receiverName").value("김받는"));

        // 하이픈 없이 넣어도 같은 결과여야 한다 — 숫자만 비교한다
        mvc.perform(lookup(orderNo, PHONE)).andExpect(status().isOk());

        cleanUp(orderNo);
    }

    @Test
    @DisplayName("연락처가 틀리면 열리지 않는다 — 주문번호를 알아도 소용없다")
    void refusesWrongPhone() throws Exception {
        String orderNo = insertGuestOrder(PHONE);

        mvc.perform(lookup(orderNo, "010-9999-8888"))
                .andExpect(status().isNotFound());

        cleanUp(orderNo);
    }

    @Test
    @DisplayName("회원 주문은 이 경로로 열리지 않는다 — 로그인해서 봐야 한다")
    void refusesMemberOrder() throws Exception {
        Long memberId = jdbc.queryForObject(
                "INSERT INTO member (email, name, status) VALUES (?, ?, 'ACTIVE') RETURNING id",
                Long.class, "guest-lookup" + System.nanoTime() + "@rizen.invalid", "회원");
        String orderNo = insertOrder(PHONE, memberId);

        mvc.perform(lookup(orderNo, "010-1234-5678"))
                .andExpect(status().isNotFound());

        cleanUp(orderNo);
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    @DisplayName("없는 주문번호와 연락처 틀린 주문의 응답이 같다 — 존재 여부를 알려주지 않는다")
    void tellsNothingAboutExistence() throws Exception {
        String orderNo = insertGuestOrder(PHONE);

        String wrongPhone = mvc.perform(lookup(orderNo, "010-9999-8888"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String noSuchOrder = mvc.perform(lookup("R20990101-NOSUCHORDER", "010-9999-8888"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(wrongPhone).isEqualTo(noSuchOrder);

        cleanUp(orderNo);
    }

    @Test
    @DisplayName("연락처 형식이 아니면 조회 자체를 받지 않는다")
    void rejectsMalformedPhone() throws Exception {
        mvc.perform(lookup("R20260101-ABCDEFGH", "1234")).andExpect(status().isBadRequest());
        mvc.perform(lookup("", "010-1234-5678")).andExpect(status().isBadRequest());
    }

    // ── 도우미 ────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder lookup(
            String orderNo, String phone) {
        return post("/api/orders/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\",\"receiverPhone\":\"" + phone + "\"}");
    }

    private String insertGuestOrder(String phone) {
        return insertOrder(phone, null);
    }

    private String insertOrder(String phone, Long memberId) {
        String orderNo = "GL" + System.nanoTime();
        jdbc.update("INSERT INTO orders (order_no, member_id, status, orderer_name, orderer_phone_encrypted, "
                        + "receiver_name, receiver_phone_encrypted, zipcode, addr1, items_amount, total_amount) "
                        + "VALUES (?, ?, 'PAID', '주문자', ?, '김받는', ?, '06234', '서울 강남구 1', 1000, 1000)",
                orderNo, memberId, cipher.encrypt(phone), cipher.encrypt(phone));
        return orderNo;
    }

    private void cleanUp(String orderNo) {
        jdbc.update("DELETE FROM orders WHERE order_no = ?", orderNo);
    }
}

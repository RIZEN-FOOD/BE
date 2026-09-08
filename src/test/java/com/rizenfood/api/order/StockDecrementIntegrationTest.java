package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.product.ProductRepository;

/**
 * 재고 원자적 차감 (CLAUDE.md 규칙 5: "읽고-나서-쓰기 금지").
 *
 * 실제 PostgreSQL(Testcontainers)에서 검증한다. decreaseStock 은
 *   UPDATE ... SET stock = stock - :qty WHERE id = :id AND stock >= :qty
 * 이므로, 재고가 모자라면 갱신 행이 0이라 주문이 막히고 재고는 절대 음수가 되지 않는다.
 * 동시 주문이 들어와도 조건이 원자적으로 평가돼 초과 판매(oversell)가 없다.
 */
@SpringBootTest
// Docker 를 못 찾는 환경(예: 일부 로컬)에서는 실패가 아니라 건너뛴다.
// CI 등 Docker 가 있는 환경에서는 실제 PostgreSQL 로 실행된다.
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class StockDecrementIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbc;

    private Long insertProduct(int stock) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) "
                        + "VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class,
                "test-" + System.nanoTime(), "테스트상품", 10000, stock);
    }

    private int stockOf(Long id) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id);
    }

    @Test
    @DisplayName("재고 이하 주문은 원자적으로 차감된다")
    void decrementsWithinStock() {
        Long id = insertProduct(5);
        assertThat(productRepository.decreaseStock(id, 3)).isEqualTo(1);
        assertThat(stockOf(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("재고보다 많이 주문하면 차감되지 않는다 (오버셀 방지)")
    void blocksOversell() {
        Long id = insertProduct(2);
        // WHERE stock >= qty 가 갱신을 막는다 → 영향 행 0
        assertThat(productRepository.decreaseStock(id, 5)).isEqualTo(0);
        assertThat(stockOf(id)).isEqualTo(2); // 재고 그대로
    }

    @Test
    @DisplayName("재고는 절대 음수가 되지 않는다")
    void neverNegative() {
        Long id = insertProduct(1);
        assertThat(productRepository.decreaseStock(id, 1)).isEqualTo(1);
        assertThat(stockOf(id)).isEqualTo(0);
        // 다 팔린 뒤 추가 주문은 막힌다
        assertThat(productRepository.decreaseStock(id, 1)).isEqualTo(0);
        assertThat(stockOf(id)).isEqualTo(0);
    }
}

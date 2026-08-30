package com.susuggang.payment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.domain.Stock;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.kafka.PaymentCompensationDlqConsumer;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.StockRepository;
import com.susuggang.scheduler.PaymentRemnantScheduler;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 결제 장애 주입 실측 — 토스 클라이언트에만 장애를 심고(무응답·거절·취소 실패·조회 실패),
// 장부 상태 기계 + 보상 + 대사 + 잔류 스캔이 끝까지 수렴하는지 건수로 확인한다.
// 실DB(5434)·실카프카(9192) 필요. 토픽은 PaymentCompensationConsumerTest와 같은 전용 토픽(컨텍스트 공유).
@SpringBootTest(properties = "payment.compensation-topic=payment-compensation-test")
class PaymentFaultInjectionTest {

    private static final int PRICE = 20000;
    private static final String APPROVED_AT = "2026-08-22T23:00:00+09:00";

    @Autowired PaymentService paymentService;
    @Autowired PaymentReconciliationService reconciliationService;
    @Autowired PaymentRemnantScheduler remnantScheduler;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired StockRepository stockRepository;
    @MockitoBean TossPaymentClient tossPaymentClient;

    Long productId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();

        productId = productRepository.save(Product.builder()
                .title("손뜨개 인형").price(PRICE).sellerId(1L).status(ProductStatus.ON_SALE).build()).getId();
        stockRepository.save(Stock.builder().productId(productId).quantity(0).build());
    }

    // ① 승인 무응답 100건 → REQUESTED 잔류 → 대사 스캔이 토스 조회 결과대로만 전이.
    //    DONE 70(그중 10은 주문 만료 → 보상 취소) · ABORTED 20 → FAILED · 조회 실패 10 → 다음 주기에 판정.
    //    승인 API 재호출은 0회여야 한다(이중 승인 금지).
    @Test
    void 승인_무응답_100건은_대사가_전부_판정하고_승인을_재호출하지_않는다() throws InterruptedException {
        int n = 100;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean expired = i % 10 == 6;
            orderIds.add(newOrder(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusMinutes(30)));
        }
        willThrow(noResponse()).given(tossPaymentClient).confirm(any());
        Map<String, AtomicInteger> findCalls = new ConcurrentHashMap<>();
        willAnswer(inv -> {
            String key = inv.getArgument(0);
            int bucket = indexOf(key) % 10;
            int call = findCalls.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            if (bucket == 7 || bucket == 8) return response(key, "ABORTED");
            if (bucket == 9 && call == 1) throw serverError("/v1/payments/" + key);
            return response(key, "DONE");
        }).given(tossPaymentClient).find(anyString());
        willAnswer(inv -> response(inv.getArgument(0), "CANCELED")).given(tossPaymentClient).cancel(anyString(), any());

        AtomicInteger rejected = new AtomicInteger();
        runConcurrently(n, i -> {
            try {
                paymentService.confirmPayment(1L, orderIds.get(i), "toss-ft_req_" + i, "ft_req_" + i, (long) PRICE);
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.PAYMENT_CONFIRM_FAILED) rejected.incrementAndGet();
            }
        });
        assertThat(rejected.get()).isEqualTo(n);
        assertThat(count(PaymentStatus.REQUESTED)).isEqualTo(n);

        long scan1 = System.nanoTime();
        remnantScheduler.scanRemnants(LocalDateTime.now().plusMinutes(11));
        long scan1Ms = (System.nanoTime() - scan1) / 1_000_000;
        Map<PaymentStatus, Long> afterScan1 = counts();
        assertThat(afterScan1.get(PaymentStatus.REQUESTED)).isEqualTo(10);   // 조회 실패분만 남는다
        assertThat(afterScan1.get(PaymentStatus.FAILED)).isEqualTo(20);
        assertThat(awaitCount(PaymentStatus.CANCELED, 10, 30_000)).isEqualTo(10);   // 승인됐지만 만료된 주문 → 보상

        long scan2 = System.nanoTime();
        remnantScheduler.scanRemnants(LocalDateTime.now().plusMinutes(11));
        long scan2Ms = (System.nanoTime() - scan2) / 1_000_000;
        Map<PaymentStatus, Long> finalCounts = counts();

        assertThat(finalCounts.get(PaymentStatus.REQUESTED)).isZero();
        assertThat(finalCounts.get(PaymentStatus.APPROVED)).isEqualTo(70);
        assertThat(finalCounts.get(PaymentStatus.FAILED)).isEqualTo(20);
        assertThat(finalCounts.get(PaymentStatus.CANCELED)).isEqualTo(10);
        assertThat(orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count())
                .isEqualTo(70);
        verify(tossPaymentClient, times(n)).confirm(any());          // 승인 재호출 0
        verify(tossPaymentClient, times(110)).find(anyString());     // 100 + 조회 실패 10건 재조회
        verify(tossPaymentClient, times(10)).cancel(anyString(), any());

        System.out.printf("[장애주입①] 무응답 %d건 → 1차 대사 %dms: %s → 2차 대사 %dms: %s | 승인 호출 %d회(재호출 0)%n",
                n, scan1Ms, afterScan1, scan2Ms, finalCounts, n);
    }

    // ② 승인은 났는데 주문 확정 실패(만료) 100건 → CANCEL_PENDING 선저장 → 커밋 후 이벤트 → 컨슈머 취소.
    //    전부 CANCELED로 수렴하고 취소 API는 건당 정확히 1회(이중 취소 0).
    @Test
    void 승인_후_확정_실패_100건은_보상_취소로_전부_되돌리고_이중_취소가_없다() throws InterruptedException {
        int n = 100;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < n; i++) orderIds.add(newOrder(LocalDateTime.now().minusMinutes(1)));
        willAnswer(inv -> response(((TossConfirmRequest) inv.getArgument(0)).paymentKey(), "DONE")).given(tossPaymentClient).confirm(any());
        willAnswer(inv -> response(inv.getArgument(0), "CANCELED")).given(tossPaymentClient).cancel(anyString(), any());

        AtomicInteger notConfirmable = new AtomicInteger();
        long t0 = System.nanoTime();
        runConcurrently(n, i -> {
            try {
                paymentService.confirmPayment(1L, orderIds.get(i), "toss-ft_comp_" + i, "ft_comp_" + i, (long) PRICE);
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.ORDER_NOT_CONFIRMABLE) notConfirmable.incrementAndGet();
            }
        });
        long requestMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(notConfirmable.get()).isEqualTo(n);

        long canceled = awaitCount(PaymentStatus.CANCELED, n, 60_000);
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(canceled).isEqualTo(n);
        assertThat(count(PaymentStatus.CANCEL_PENDING)).isZero();
        assertThat(count(PaymentStatus.APPROVED)).isZero();
        verify(tossPaymentClient, times(n)).cancel(anyString(), any());   // 이중 취소 0

        System.out.printf("[장애주입②] 확정 실패 %d건 → 요청 응답 %dms(취소 안 기다림) → 전부 CANCELED %dms | 취소 호출 %d회(이중 0)%n",
                n, requestMs, totalMs, n);
    }

    // ③ 취소 API 장애 10건 → 재시도(1초×3) 소진 → DLQ 격리, 상태는 CANCEL_PENDING 유지 →
    //    잔류 스캔이 재투입(카운트 1) → 토스 복구 후 CANCELED. 상한(3) 초과 없음.
    @Test
    void 취소_API_장애_10건은_DLQ로_격리되고_잔류_스캔_재투입으로_복구된다() throws InterruptedException {
        Logger dlqLogger = (Logger) LoggerFactory.getLogger(PaymentCompensationDlqConsumer.class);
        ListAppender<ILoggingEvent> dlqLogs = new ListAppender<>();
        dlqLogs.start();
        dlqLogger.addAppender(dlqLogs);
        try {
            int n = 10;
            List<Long> orderIds = new ArrayList<>();
            for (int i = 0; i < n; i++) orderIds.add(newOrder(LocalDateTime.now().minusMinutes(1)));
            willAnswer(inv -> response(((TossConfirmRequest) inv.getArgument(0)).paymentKey(), "DONE")).given(tossPaymentClient).confirm(any());
            Map<String, AtomicInteger> cancelCalls = new ConcurrentHashMap<>();
            willAnswer(inv -> {
                String key = inv.getArgument(0);
                int call = cancelCalls.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                if (call <= 4) throw serverError("/v1/payments/" + key + "/cancel");   // 최초 1 + 재시도 3 전부 실패
                return response(key, "CANCELED");
            }).given(tossPaymentClient).cancel(anyString(), any());

            runConcurrently(n, i -> {
                try {
                    paymentService.confirmPayment(1L, orderIds.get(i), "toss-ft_dlq_" + i, "ft_dlq_" + i, (long) PRICE);
                } catch (BusinessException ignored) {
                }
            });

            // 재시도 소진까지 대기: 건당 4회 시도
            verify(tossPaymentClient, timeout(90_000).times(n * 4)).cancel(anyString(), any());
            Thread.sleep(3000);   // DLQ 발행·소비 여유
            long dlqCount = dlqLogs.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("DLQ")).count();
            assertThat(dlqCount).isEqualTo(n);
            assertThat(count(PaymentStatus.CANCEL_PENDING)).isEqualTo(n);
            assertThat(count(PaymentStatus.CANCELED)).isZero();

            // 잔류 스캔 재투입 → 컨슈머 단일 경로 → 이제 토스가 복구됐으므로 CANCELED
            remnantScheduler.scanRemnants(LocalDateTime.now().plusMinutes(11));
            long recovered = awaitCount(PaymentStatus.CANCELED, n, 30_000);

            assertThat(recovered).isEqualTo(n);
            assertThat(count(PaymentStatus.CANCEL_FAILED)).isZero();
            assertThat(paymentRepository.findAll()).allSatisfy(p -> assertThat(p.getCancelRetryCount()).isEqualTo(1));
            verify(tossPaymentClient, times(n * 5)).cancel(anyString(), any());

            System.out.printf("[장애주입③] 취소 장애 %d건 → 시도 %d회 → DLQ %d건(CANCEL_PENDING 유지) → 스캔 재투입 → 복구 %d건, 상한 초과 0%n",
                    n, n * 4, dlqCount, recovered);
        } finally {
            dlqLogger.detachAppender(dlqLogs);
        }
    }

    // ④ 대사 스캔과 사용자 재요청이 같은 REQUESTED 건에 동시에 도착 — settleRequested 가드로 승인 전이는 한 번.
    //    끝 상태는 APPROVED + 주문 COMPLETED여야 하고 보상(CANCEL_PENDING/CANCELED)이 생기면 안 된다.
    @Test
    void 대사_스캔과_사용자_재요청이_동시에_와도_승인_전이는_한_번이고_보상이_생기지_않는다() throws InterruptedException {
        int n = 20;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < n; i++) orderIds.add(newOrder(LocalDateTime.now().plusMinutes(30)));
        willThrow(noResponse()).given(tossPaymentClient).confirm(any());
        runConcurrently(n, i -> {
            try {
                paymentService.confirmPayment(1L, orderIds.get(i), "toss-ft_race_" + i, "ft_race_" + i, (long) PRICE);
            } catch (BusinessException ignored) {
            }
        });
        assertThat(count(PaymentStatus.REQUESTED)).isEqualTo(n);

        // 토스 쪽에선 사실 승인돼 있었다 — 이제 조회도 재승인 요청도 DONE을 돌려준다
        willAnswer(inv -> response(((TossConfirmRequest) inv.getArgument(0)).paymentKey(), "DONE")).given(tossPaymentClient).confirm(any());
        willAnswer(inv -> response(inv.getArgument(0), "DONE")).given(tossPaymentClient).find(anyString());
        willAnswer(inv -> response(inv.getArgument(0), "CANCELED")).given(tossPaymentClient).cancel(anyString(), any());

        List<Payment> requested = paymentRepository.findAll();
        AtomicInteger userNotConfirmable = new AtomicInteger();
        AtomicInteger userOk = new AtomicInteger();
        Map<PaymentReconciliationService.Result, AtomicInteger> scanResults = new ConcurrentHashMap<>();
        runConcurrently(n * 2, slot -> {
            Payment p = requested.get(slot / 2);
            if (slot % 2 == 0) {
                scanResults.computeIfAbsent(reconciliationService.reconcile(p), r -> new AtomicInteger()).incrementAndGet();
            } else {
                try {
                    paymentService.confirmPayment(1L, p.getOrderId(), p.getTossOrderId(), p.getPaymentKey(), (long) PRICE);
                    userOk.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ORDER_NOT_CONFIRMABLE) userNotConfirmable.incrementAndGet();
                }
            }
        });
        Thread.sleep(3000);   // 보상이 생겼다면 컨슈머가 움직일 시간

        Map<PaymentStatus, Long> finalCounts = counts();
        long completedOrders = orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        System.out.printf("[장애주입④] 경합 %d건 → 스캔 결과 %s · 사용자 성공 %d · 사용자 확정불가 %d → 장부 %s · 주문 COMPLETED %d%n",
                n, scanResults, userOk.get(), userNotConfirmable.get(), finalCounts, completedOrders);

        assertThat(completedOrders).isEqualTo(n);
        assertThat(finalCounts.get(PaymentStatus.APPROVED)).isEqualTo(n);
        assertThat(finalCounts.get(PaymentStatus.CANCEL_PENDING) + finalCounts.get(PaymentStatus.CANCELED)).isZero();
        verify(tossPaymentClient, never()).cancel(anyString(), any());
    }

    private Long newOrder(LocalDateTime expiresAt) {
        return orderRepository.save(Order.builder()
                .buyerId(1L).productId(productId).status(OrderStatus.RESERVED).expiresAt(expiresAt).build()).getId();
    }

    private TossPaymentResponse response(String paymentKey, String status) {
        return new TossPaymentResponse(paymentKey, "toss-" + paymentKey, status, "간편결제", (long) PRICE, APPROVED_AT);
    }

    private int indexOf(String paymentKey) {
        return Integer.parseInt(paymentKey.substring(paymentKey.lastIndexOf('_') + 1));
    }

    // 읽기 타임아웃 때 Feign이 실제로 던지는 예외 — status -1(응답 없음). mock 예외는 스택트레이스가 null이라
    // 테스트 러너 보고 단계에서 NPE를 내므로 진짜 인스턴스로
    private FeignException noResponse() {
        Request request = Request.create(Request.HttpMethod.POST, "/v1/payments/confirm",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request);
    }

    // 카프카 경로를 타는 예외는 진짜 인스턴스여야 한다 — mock은 DLQ 발행기의 예외 헤더 기록에서 NPE
    private FeignException serverError(String path) {
        Request request = Request.create(Request.HttpMethod.POST, path, Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.FeignServerException(500, "toss down", request, null, Map.of());
    }

    private void runConcurrently(int n, IntConsumer task) throws InterruptedException {
        // 스레드 수 = 작업 수 — 출발 게이트(start) 앞에서 전원이 대기해야 하므로 풀이 작으면 교착된다
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(idx);
                } catch (Throwable t) {
                    System.err.println("task " + idx + " failed: " + t);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();   // 전원 준비된 뒤 동시에 출발
        assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    private long count(PaymentStatus status) {
        return paymentRepository.findAll().stream().filter(p -> p.getStatus() == status).count();
    }

    private Map<PaymentStatus, Long> counts() {
        Map<PaymentStatus, Long> result = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus s : PaymentStatus.values()) result.put(s, 0L);
        paymentRepository.findAll().forEach(p -> result.merge(p.getStatus(), 1L, Long::sum));
        return result;
    }

    private long awaitCount(PaymentStatus status, long expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long current = count(status);
        while (current < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            current = count(status);
        }
        return current;
    }
}

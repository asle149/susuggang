import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 성공(201)·품절(409)을 따로 센다 — 성공 수가 정확히 재고 수와 같아야 oversell 0
const orderSuccess = new Counter('order_success');
const orderSoldout = new Counter('order_soldout');

export const options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations', // VU 200명이 각자 1발 = 동시 200 사격
            vus: 200,
            iterations: 1,
        },
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID;

// setup은 사격 전에 딱 1번 실행 — 로그인해서 토큰을 받아 모든 VU에게 나눠준다
export function setup() {
    const res = http.post(`${BASE}/auth/login`,
        JSON.stringify({ email: 'minsol@test.com', password: 'pass1234' }),
        { headers: { 'Content-Type': 'application/json' } });
    return { token: res.json('token') };
}

// 각 VU가 실행하는 본체
export default function (data) {
    const res = http.post(`${BASE}/orders`,
        JSON.stringify({ productId: Number(PRODUCT_ID) }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${data.token}`,
            },
        });

    if (res.status === 201) orderSuccess.add(1);
    else if (res.status === 409) orderSoldout.add(1);

    check(res, { '201(주문) 또는 409(품절)': (r) => r.status === 201 || r.status === 409 });
}

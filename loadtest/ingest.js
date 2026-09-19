// Teste de carga com k6 — valida os requisitos não funcionais:
//   regime normal 5.000 ev/s, pico 20.000 ev/s, p95 < 200ms.
//
// Uso (aplicação com perfil "local"):
//   k6 run loadtest/ingest.js
//   k6 run -e BASE_URL=https://events.example.com -e TOKEN=<jwt> loadtest/ingest.js
//
// Cada iteração envia 1 evento (endpoint unitário), que é o pior caso de latência.
// Com o endpoint /batch, a mesma vazão exige ~50x menos requisições.
import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    regime_normal: {
      executor: 'constant-arrival-rate',
      rate: 5000, timeUnit: '1s', duration: '5m',
      preAllocatedVUs: 400, maxVUs: 2000,
    },
    pico: {
      executor: 'ramping-arrival-rate',
      startTime: '5m',
      startRate: 5000, timeUnit: '1s',
      preAllocatedVUs: 1500, maxVUs: 6000,
      stages: [
        { target: 20000, duration: '1m' },  // rampa até o pico
        { target: 20000, duration: '3m' },  // sustenta o pico
        { target: 5000, duration: '1m' },   // volta ao normal
      ],
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<200'],
    'http_req_failed': ['rate<0.001'],
    'checks': ['rate>0.999'],
  },
};

export function setup() {
  if (__ENV.TOKEN) return { token: __ENV.TOKEN };
  const res = http.post(`${BASE_URL}/dev/token?tenant=loadtest`, null, { responseType: 'text' });
  return { token: res.json('access_token') };
}

const TYPES = ['order.created', 'order.paid', 'payment.authorized', 'shipment.dispatched'];

export default function (data) {
  const body = JSON.stringify({
    type: TYPES[Math.floor(Math.random() * TYPES.length)],
    source: `svc-${Math.floor(Math.random() * 50)}`,
    payload: { orderId: uuidv4(), amount: Math.round(Math.random() * 100000) / 100, currency: 'BRL' },
  });
  const res = http.post(`${BASE_URL}/api/v1/events`, body, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'Idempotency-Key': uuidv4(),
    },
  });
  check(res, { 'aceito (202)': (r) => r.status === 202 });
}

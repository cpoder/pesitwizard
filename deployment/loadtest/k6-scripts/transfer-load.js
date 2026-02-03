/**
 * PeSIT Wizard Load Test - Transfer Simulation
 *
 * This k6 script simulates API load for the PeSIT server.
 * It tests the HTTP management API, not the PeSIT protocol directly.
 *
 * Usage:
 *   k6 run transfer-load.js
 *   k6 run --vus 100 --duration 30m transfer-load.js
 *   K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write k6 run -o experimental-prometheus-rw transfer-load.js
 *
 * Environment variables:
 *   BASE_URL: Server base URL (default: http://localhost:8080)
 *   API_KEY: API key for authentication
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const transferSuccess = new Counter('pesitwizard_transfers_success');
const transferFailed = new Counter('pesitwizard_transfers_failed');
const transferDuration = new Trend('pesitwizard_transfer_duration');
const errorRate = new Rate('pesitwizard_error_rate');
const activeTransfers = new Gauge('pesitwizard_active_transfers');

// Test configuration
export const options = {
    scenarios: {
        // Ramp up to target load
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5m', target: 100 },   // Ramp up to 100 users
                { duration: '30m', target: 200 },  // Sustain at 200 users
                { duration: '5m', target: 0 },     // Ramp down
            ],
            gracefulRampDown: '2m',
        },
    },
    thresholds: {
        // P95 response time should be under 30 seconds
        http_req_duration: ['p(95)<30000'],
        // Error rate should be under 1%
        http_req_failed: ['rate<0.01'],
        // Custom metrics
        pesitwizard_error_rate: ['rate<0.01'],
        pesitwizard_transfer_duration: ['p(95)<30000'],
    },
    // Tags for organizing results
    tags: {
        testType: 'load',
        environment: 'staging',
    },
};

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || '';

const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

if (API_KEY) {
    headers['X-API-Key'] = API_KEY;
}

// Setup - runs once at the start
export function setup() {
    console.log(`Starting load test against ${BASE_URL}`);

    // Verify server is healthy
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    check(healthRes, {
        'server is healthy': (r) => r.status === 200,
    });

    if (healthRes.status !== 200) {
        throw new Error('Server is not healthy, aborting test');
    }

    return {
        baseUrl: BASE_URL,
        startTime: new Date().toISOString(),
    };
}

// Main test function
export default function(data) {
    // Randomly select a scenario
    const scenario = randomIntBetween(1, 100);

    if (scenario <= 60) {
        // 60% - Check transfer status
        checkTransferStatus(data);
    } else if (scenario <= 85) {
        // 25% - List active transfers
        listTransfers(data);
    } else if (scenario <= 95) {
        // 10% - Check cluster status
        checkClusterStatus(data);
    } else {
        // 5% - Health and metrics checks
        healthChecks(data);
    }

    // Random sleep between requests (1-3 seconds)
    sleep(randomIntBetween(1, 3));
}

function checkTransferStatus(data) {
    group('Check Transfer Status', function() {
        const startTime = Date.now();

        // Simulate checking transfer status
        const res = http.get(`${data.baseUrl}/api/v1/transfers`, { headers });

        const duration = Date.now() - startTime;
        transferDuration.add(duration);

        const success = check(res, {
            'status is 200': (r) => r.status === 200,
            'response has transfers': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return Array.isArray(body) || body.content !== undefined;
                } catch {
                    return false;
                }
            },
        });

        if (success) {
            transferSuccess.add(1);
            errorRate.add(0);
        } else {
            transferFailed.add(1);
            errorRate.add(1);
        }
    });
}

function listTransfers(data) {
    group('List Transfers', function() {
        const page = randomIntBetween(0, 10);
        const size = 20;

        const res = http.get(
            `${data.baseUrl}/api/v1/transfers?page=${page}&size=${size}`,
            { headers }
        );

        check(res, {
            'status is 200': (r) => r.status === 200,
            'response time OK': (r) => r.timings.duration < 5000,
        });
    });
}

function checkClusterStatus(data) {
    group('Check Cluster Status', function() {
        const res = http.get(`${data.baseUrl}/api/v1/cluster/status`, { headers });

        check(res, {
            'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
            'cluster info present': (r) => {
                if (r.status !== 200) return true;
                try {
                    const body = JSON.parse(r.body);
                    return body.members !== undefined || body.nodeCount !== undefined;
                } catch {
                    return false;
                }
            },
        });
    });
}

function healthChecks(data) {
    group('Health Checks', function() {
        // Health endpoint
        const healthRes = http.get(`${data.baseUrl}/actuator/health`);
        check(healthRes, {
            'health status is 200': (r) => r.status === 200,
            'health is UP': (r) => {
                try {
                    return JSON.parse(r.body).status === 'UP';
                } catch {
                    return false;
                }
            },
        });

        // Metrics endpoint
        const metricsRes = http.get(`${data.baseUrl}/actuator/prometheus`);
        check(metricsRes, {
            'metrics status is 200': (r) => r.status === 200,
            'metrics has content': (r) => r.body.length > 100,
        });
    });
}

// Teardown - runs once at the end
export function teardown(data) {
    console.log(`Load test completed. Started at: ${data.startTime}`);

    // Final health check
    const healthRes = http.get(`${data.baseUrl}/actuator/health`);
    const serverHealthy = healthRes.status === 200;

    console.log(`Server health after test: ${serverHealthy ? 'HEALTHY' : 'DEGRADED'}`);
}

// Handle test summary
export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'summary.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data, options) {
    const metrics = data.metrics;

    return `
================================================================================
                           PeSIT WIZARD LOAD TEST RESULTS
================================================================================

Duration: ${data.state.testRunDurationMs / 1000}s
VUs Max:  ${data.root_group.checks.length}

HTTP Requests:
  Total:    ${metrics.http_reqs.values.count}
  Rate:     ${metrics.http_reqs.values.rate.toFixed(2)}/s

Response Times:
  Avg:      ${metrics.http_req_duration.values.avg.toFixed(2)}ms
  Min:      ${metrics.http_req_duration.values.min.toFixed(2)}ms
  Max:      ${metrics.http_req_duration.values.max.toFixed(2)}ms
  P90:      ${metrics.http_req_duration.values['p(90)'].toFixed(2)}ms
  P95:      ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
  P99:      ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms

Error Rate:
  Failed:   ${metrics.http_req_failed.values.rate * 100}%

Custom Metrics:
  Transfer Success: ${metrics.pesitwizard_transfers_success?.values?.count || 0}
  Transfer Failed:  ${metrics.pesitwizard_transfers_failed?.values?.count || 0}

Thresholds:
  http_req_duration p(95) < 30s: ${metrics.http_req_duration.values['p(95)'] < 30000 ? 'PASS' : 'FAIL'}
  http_req_failed rate < 1%:    ${metrics.http_req_failed.values.rate < 0.01 ? 'PASS' : 'FAIL'}

================================================================================
`;
}

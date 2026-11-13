<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
define('WIZE_REVIEWS_ENTRY', true);
require __DIR__ . '/_private/bootstrap.php';
try {
    wize_require_https();
    $headers = wize_headers();
    $raw = file_get_contents('php://input', false, null, 0, 16385);
    $action = $_GET['action'] ?? '';
    if (!is_string($action) || strlen($_SERVER['REQUEST_URI'] ?? '') > 1024) {
        wize_send(['status' => 400, 'headers' => ['Content-Type' => 'application/json', 'Cache-Control' => 'no-store'], 'body' => '{"error":"Invalid request."}']);
    }
    $ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
    // REMOTE_ADDR is supplied by the server. Forwarded address headers are ignored.
    wize_send(wize_service()->handle($_SERVER['REQUEST_METHOD'] ?? 'GET', $action, $_GET, $headers, $raw === false ? '' : $raw, $ip));
} catch (Throwable) { wize_unavailable(); }

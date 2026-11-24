<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
define('WIZE_REVIEWS_ENTRY', true);
require __DIR__ . '/_private/bootstrap.php';
require __DIR__ . '/_private/request.php';
$service = null;
try {
    wize_require_https();
    $headers = wize_headers();
    $action = $_GET['action'] ?? '';
    if (!is_string($action) || strlen($_SERVER['REQUEST_URI'] ?? '') > 1024) {
        wize_send(['status' => 400, 'headers' => ['Content-Type' => 'application/json', 'Cache-Control' => 'no-store'], 'body' => '{"error":"Invalid request."}']);
    }
    $ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
    // REMOTE_ADDR is supplied by the server. Forwarded address headers are ignored.
    $service = wize_service();
    [$headers, $raw, $files] = wize_request($action, $headers);
    wize_send($service->handle($_SERVER['REQUEST_METHOD'] ?? 'GET', $action, $_GET, $headers, $raw, $ip, $files));
} catch (ReviewHttpError $error) {
    wize_send(['status' => $error->status, 'headers' => $service?->headers() ?? ['Content-Type' => 'application/json', 'Cache-Control' => 'no-store'],
        'body' => json_encode(['error' => $error->getMessage()], JSON_THROW_ON_ERROR)]);
} catch (Throwable) { wize_unavailable(); }

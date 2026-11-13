<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
define('WIZE_REVIEWS_ENTRY', true);
require __DIR__ . '/_private/bootstrap.php';
try {
    wize_require_https();
    $format = $_GET['format'] ?? 'svg';
    $action = $format === 'json' ? 'badge-json' : ($format === 'svg' ? 'badge-svg' : '');
    wize_send(wize_service()->handle($_SERVER['REQUEST_METHOD'] ?? 'GET', $action));
} catch (Throwable) { wize_unavailable(); }

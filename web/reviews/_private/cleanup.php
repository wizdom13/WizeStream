<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
define('WIZE_REVIEWS_ENTRY', true);
require __DIR__ . '/bootstrap.php';
try {
    // CLI has no document root: set WIZESTREAM_REVIEWS_CONFIG to the private file.
    $_SERVER['DOCUMENT_ROOT'] = dirname(__DIR__, 2);
    wize_service()->maintenance();
    echo "Expired review data removed.\n";
} catch (Throwable) {
    fwrite(STDERR, "Review maintenance failed. Check the private configuration and schema.\n");
    exit(1);
}

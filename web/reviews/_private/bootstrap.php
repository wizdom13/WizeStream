<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY')) { http_response_code(404); exit; }
ini_set('display_errors', '0');
require_once __DIR__ . '/service.php';

function wize_config(): array
{
    $documentRoot = realpath($_SERVER['DOCUMENT_ROOT'] ?? dirname(__DIR__, 2));
    if (!$documentRoot) { throw new RuntimeException('Set the document root.'); }
    $path = getenv('WIZESTREAM_REVIEWS_CONFIG') ?: dirname($documentRoot) . '/wizestream-private/reviews-config.php';
    $real = realpath($path);
    $root = rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
    $source = rtrim((string) realpath(dirname(__DIR__, 2)), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
    if (!$real || !is_file($real) || str_starts_with($real, $root) || str_starts_with($real, $source)) {
        throw new RuntimeException('Place reviews-config.php outside every public document root.');
    }
    $config = require $real;
    if (!is_array($config)) { throw new RuntimeException('Invalid configuration.'); }
    ReviewService::validateConfig($config);
    return $config;
}

function wize_service(): ReviewService
{
    $config = wize_config();
    $database = $config['database'] ?? [];
    $host = $database['host'] ?? 'localhost'; $port = $database['port'] ?? 3306;
    $name = $database['name'] ?? '';
    if (!is_string($host) || !preg_match('/^[A-Za-z0-9_.:\[\]-]+$/D', $host) || !is_int($port) || $port < 1 || $port > 65535 ||
        !is_string($name) || !preg_match('/^[A-Za-z0-9_]+$/D', $name)) { throw new RuntimeException('Invalid database configuration.'); }
    $db = new PDO("mysql:host=$host;port=$port;dbname=$name;charset=utf8mb4", $database['username'] ?? '', $database['password'] ?? '', [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    return new ReviewService($db, $config);
}

function wize_headers(): array
{
    $headers = [];
    foreach ($_SERVER as $name => $value) {
        if (str_starts_with($name, 'HTTP_') && is_string($value)) { $headers[strtolower(str_replace('_', '-', substr($name, 5)))] = $value; }
    }
    foreach (['CONTENT_TYPE' => 'content-type', 'CONTENT_LENGTH' => 'content-length'] as $name => $header) {
        if (isset($_SERVER[$name])) { $headers[$header] = (string) $_SERVER[$name]; }
    }
    return $headers;
}

function wize_require_https(): void
{
    $url = parse_url(wize_config()['public_url']);
    if ($url['scheme'] === 'https' && !in_array(strtolower((string) ($_SERVER['HTTPS'] ?? '')), ['on', '1'], true)) {
        // Do not trust client-supplied X-Forwarded-Proto or Host headers.
        throw new RuntimeException('Enable HTTPS and HTTPS redirection in cPanel.');
    }
}

function wize_send(array $response): never
{
    http_response_code($response['status']);
    foreach ($response['headers'] as $name => $value) { header($name . ': ' . $value); }
    echo $response['body'];
    exit;
}

function wize_unavailable(): never
{
    wize_send(['status' => 503, 'headers' => ['Content-Type' => 'application/json; charset=utf-8', 'Cache-Control' => 'no-store', 'Referrer-Policy' => 'no-referrer', 'X-Content-Type-Options' => 'nosniff'],
        'body' => '{"error":"Community reviews are not available yet. Please try again later."}']);
}

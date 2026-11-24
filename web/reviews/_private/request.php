<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY')) { http_response_code(404); exit; }

/** Parse bounded multipart at the HTTP boundary; the service still validates a small JSON object. */
function wize_request(string $action, array $headers): array
{
    $type = strtolower(trim(explode(';', $headers['content-type'] ?? '')[0]));
    if ($type !== 'multipart/form-data') {
        return [$headers, (string) file_get_contents('php://input', false, null, 0, 16385), []];
    }
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST' || !in_array($action, ['reviews', 'manage'], true) ||
        !empty($headers['content-encoding'])) { throw new ReviewHttpError(415, 'Images can only accompany a review or review edit.'); }
    if ((int) ($headers['content-length'] ?? 0) > ReviewImages::MAX_REQUEST_BYTES) {
        throw new ReviewHttpError(413, 'Attach at most three images of up to 5 MiB each.');
    }
    if ($_POST === [] && $_FILES === []) {
        throw new ReviewHttpError(413, 'This submission exceeds the server upload limit. Try smaller images or send them by email.');
    }
    if (array_keys($_POST) !== ['payload'] || !is_string($_POST['payload']) || strlen($_POST['payload']) > 16384 ||
        array_diff(array_keys($_FILES), ['images']) !== []) { throw new ReviewHttpError(400, 'Invalid image submission.'); }
    $uploads = $_FILES['images'] ?? null;
    $files = [];
    if ($uploads !== null) {
        if (!is_array($uploads) || !is_array($uploads['error'] ?? null) || !is_array($uploads['tmp_name'] ?? null) || !array_is_list($uploads['error']) ||
            count($uploads['error']) > ReviewImages::MAX_COUNT) { throw new ReviewHttpError(400, 'Attach at most three images.'); }
        foreach ($uploads['error'] as $index => $error) {
            $path = $uploads['tmp_name'][$index] ?? null;
            if (!is_int($error) || ($error === UPLOAD_ERR_OK && (!is_string($path) || !is_uploaded_file($path)))) {
                throw new ReviewHttpError(400, 'Invalid uploaded image.');
            }
            $files[] = ['error' => $error, 'tmp_name' => $path];
        }
    }
    try { $payload = json_decode($_POST['payload'], true, 32, JSON_THROW_ON_ERROR); }
    catch (JsonException) { throw new ReviewHttpError(400, 'Invalid image submission.'); }
    if (!is_array($payload) || !is_int($payload['newImageCount'] ?? null) || $payload['newImageCount'] !== count($files)) {
        throw new ReviewHttpError(400, 'Some images did not arrive. Try fewer or smaller images, or send them by email.');
    }
    $headers['content-type'] = 'application/json';
    $headers['content-length'] = (string) strlen($_POST['payload']);
    return [$headers, $_POST['payload'], $files];
}

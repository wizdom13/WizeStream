<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY') || PHP_SAPI !== 'cli') { http_response_code(404); exit; }

// Included by reviews.php: exercise real GD decoding and the disposable MySQL database.
$imageTestDirectories = [];
register_shutdown_function(static function () use (&$imageTestDirectories): void {
    foreach ($imageTestDirectories as $directory) {
        foreach (glob($directory . '/*') ?: [] as $file) { if (is_file($file) || is_link($file)) { unlink($file); } }
        rmdir($directory);
    }
});
function imageFixture(array $options = []): array
{
    global $imageTestDirectories;
    $directory = sys_get_temp_dir() . '/wize-review-images-test-' . bin2hex(random_bytes(8));
    mkdir($directory, 0700); $imageTestDirectories[] = $directory;
    return [...fixture(array_replace(['images_dir' => $directory], $options)), $directory];
}
function imageFile(string $directory, string $type = 'png', int $width = 60, int $height = 40): array
{
    $path = $directory . '/fixture-' . bin2hex(random_bytes(6)) . '.input';
    $image = imagecreatetruecolor($width, $height);
    $colors = [0xff0000, 0x00ff00, 0x0000ff, 0xffff00];
    foreach ($colors as $index => $color) {
        $x = ($index % 2) * intdiv($width, 2); $y = intdiv($index, 2) * intdiv($height, 2);
        imagefilledrectangle($image, $x, $y, $x + intdiv($width, 2) - 1, $y + intdiv($height, 2) - 1, $color);
    }
    match ($type) { 'png' => imagepng($image, $path), 'jpeg' => imagejpeg($image, $path, 100), 'webp' => imagewebp($image, $path, 100) };
    imagedestroy($image);
    return ['tmp_name' => $path, 'error' => UPLOAD_ERR_OK];
}
function imagePost(ReviewService $service, string $action, array $data, array $files, array $headers = []): array
{
    $data = array_replace(['newImageCount' => count($files), 'imageConsent' => true], $data);
    return $service->handle('POST', $action, [], array_replace(['origin' => 'https://wizestream.org', 'content-type' => 'application/json'], $headers),
        json_encode((object) $data, JSON_THROW_ON_ERROR), '192.0.2.1', $files);
}
function imageAuthor(array $review): array { return ['x-review-authorization' => 'Review ' . $review['id'] . ':' . $review['deletionToken']]; }
function imageGet(ReviewService $service, string $id, array $headers = [], string $variant = 'full'): array
{
    return $service->handle('GET', 'image', ['id' => $id, 'variant' => $variant], $headers);
}
function imageFiles(string $directory): array { return glob($directory . '/*.webp') ?: []; }
function imageEdits(array $ids = []): array
{
    return ['operation' => 'edit', 'rating' => 2, 'content' => 'Updated review with clearer screenshots.', 'keepImageIds' => $ids];
}

/** Deterministically recreate an ID after early ownership lookup, before its guarded write. */
final class ReplacingReviewStatement extends PDOStatement
{
    protected function __construct(private stdClass $replacement) {}
    public function fetch(int $mode = PDO::FETCH_DEFAULT, int $cursorOrientation = PDO::FETCH_ORI_NEXT, int $cursorOffset = 0): mixed
    {
        $row = parent::fetch($mode, $cursorOrientation, $cursorOffset);
        if ($row && $this->queryString === 'SELECT * FROM wize_reviews WHERE id = ?' && !$this->replacement->fired) {
            $this->replacement->fired = true;
            ($this->replacement->replace)($row);
        }
        return $row;
    }
}

$tests['an old receipt cannot edit cancel or delete a replacement owner after preparation'] = function (): void {
    foreach (['edit', 'cancel-edit', 'delete'] as $operation) {
        [$s, $clock, $db, $config, $directory] = imageFixture();
        $review = draft($s, $clock->now); equal(submit($s, $review)['status'], 201);
        $replacement = new stdClass(); $replacement->fired = false;
        $replacement->replace = static function (array $row): void {
            $other = testDatabase();
            $other->prepare('DELETE FROM wize_reviews WHERE id = ?')->execute([$row['id']]);
            $other->prepare("INSERT INTO wize_reviews (id, rating, nickname, content, app_version, delete_hash, state, created_day, submitted_at) VALUES (?, 4, '', 'Replacement owner review.', '', ?, 'published', ?, ?)")
                ->execute([$row['id'], hash('sha256', str_repeat('z', 43)), $row['created_day'], $row['submitted_at']]);
            $other->prepare("INSERT INTO wize_review_edits (review_id, rating, nickname, content, app_version, submitted_at) VALUES (?, 3, '', 'Replacement owner proposed edit.', '', ?)")
                ->execute([$row['id'], $row['submitted_at']]);
        };
        $db->setAttribute(PDO::ATTR_STATEMENT_CLASS, [ReplacingReviewStatement::class, [$replacement]]);
        $racing = new ReviewService($db, $config, static fn(): int => $clock->now);
        $response = $operation === 'edit'
            ? imagePost($racing, 'manage', imageEdits(), [imageFile($directory)], imageAuthor($review))
            : manage($racing, $review, $operation);
        equal($replacement->fired, true); equal($response['status'], 404, $operation);
        equal($db->query('SELECT content FROM wize_reviews')->fetchColumn(), 'Replacement owner review.');
        equal($db->query('SELECT content FROM wize_review_edits')->fetchColumn(), 'Replacement owner proposed edit.');
        equal(imageFiles($directory), [], 'Unclaimed images after changed ownership');
    }
};

$tests['images decode to metadata-free bounded WebP and stay private until approved'] = function (): void {
    [$s, $clock, $db, $config, $directory] = imageFixture();
    equal(payload($s->handle('GET', 'challenge'))['imageLimits']['maxCount'], 3);
    $review = draft($s, $clock->now);
    $uploads = [imageFile($directory, 'jpeg'), imageFile($directory, 'png', 3000, 600), imageFile($directory, 'webp')];
    file_put_contents($uploads[0]['tmp_name'], "private-exif-marker@example.invalid", FILE_APPEND);
    equal(imagePost($s, 'reviews', $review, $uploads)['status'], 201);
    $images = payload(manage($s, $review))['images']; equal(count($images), 3);
    equal(count(imageFiles($directory)), 6); equal($images[1]['width'], 2048);
    $moderator = ['x-moderator-key' => $config['moderator_key']];
    foreach ($images as $image) {
        equal(imageGet($s, $image['id'])['status'], 404);
        $response = imageGet($s, $image['id'], imageAuthor($review));
        equal($response['status'], 200); equal($response['headers']['Content-Type'], 'image/webp');
        equal($response['headers']['Cache-Control'], 'no-store');
        equal($response['headers']['Cross-Origin-Resource-Policy'], 'same-origin');
        equal(str_contains($response['body'], 'private-exif-marker'), false);
        equal(str_contains($response['body'], 'Exif'), false);
        $decoded = imagecreatefromstring($response['body']); equal(imagesx($decoded), $image['width']); imagedestroy($decoded);
        equal(imageGet($s, $image['id'], $moderator)['status'], 200);
        $thumb = imagecreatefromstring(imageGet($s, $image['id'], imageAuthor($review), 'thumb')['body']);
        equal(max(imagesx($thumb), imagesy($thumb)) <= 384, true); imagedestroy($thumb);
    }
    $other = draft($s, $clock->now); equal(submit($s, $other)['status'], 201);
    equal(imageGet($s, $images[0]['id'], imageAuthor($other))['status'], 404);
    equal(imageGet($s, $images[0]['id'], ['x-moderator-key' => 'wrong'])['status'], 401);
    equal(moderate($s, $review['id'])['status'], 200);
    equal(imageGet($s, $images[0]['id'])['status'], 200);
    $public = payload($s->handle('GET', 'reviews'))['reviews'][0]; equal($public['images'], $images);
    foreach ([$review['deletionToken'], $directory, 'tmp_name', 'fixture-', 'private-exif-marker'] as $private) {
        equal(str_contains(json_encode($public), $private), false);
    }
    equal(imageGet($s, '../config.php')['status'], 404);
    equal(imageGet($s, $images[0]['id'], [], '../config.php')['status'], 404);
    equal(imagePost($s, 'reviews', $review, $uploads)['status'], 200); equal(count(imageFiles($directory)), 6);
    equal(manage($s, $review, 'delete')['status'], 200); equal(imageFiles($directory), []);
    equal(imageGet($s, $images[0]['id'])['status'], 404);
    equal((int) $db->query('SELECT COUNT(*) FROM wize_review_images')->fetchColumn(), 0);
};

$tests['all EXIF orientations are normalized before JPEG metadata is discarded'] = function (): void {
    [$s, $clock, , , $directory] = imageFixture();
    $orders = [1 => [0, 1, 2, 3], 2 => [1, 0, 3, 2], 3 => [3, 2, 1, 0], 4 => [2, 3, 0, 1],
        5 => [0, 2, 1, 3], 6 => [2, 0, 3, 1], 7 => [3, 1, 2, 0], 8 => [1, 3, 0, 2]];
    $colors = [[255, 0, 0], [0, 255, 0], [0, 0, 255], [255, 255, 0]];
    foreach ($orders as $orientation => $expected) {
        $file = imageFile($directory, 'jpeg', 60, 40);
        $jpeg = file_get_contents($file['tmp_name']);
        // Minimal valid little-endian TIFF IFD0 with one Orientation entry.
        $exif = "Exif\0\0II\x2a\0\x08\0\0\0\x01\0\x12\x01\x03\0\x01\0\0\0" . pack('v', $orientation) . "\0\0\0\0\0\0";
        file_put_contents($file['tmp_name'], substr($jpeg, 0, 2) . "\xff\xe1" . pack('n', strlen($exif) + 2) . $exif . substr($jpeg, 2));
        $review = draft($s, $clock->now); equal(imagePost($s, 'reviews', $review, [$file])['status'], 201);
        $metadata = payload(manage($s, $review))['images'][0];
        equal([$metadata['width'], $metadata['height']], $orientation <= 4 ? [60, 40] : [40, 60]);
        $body = imageGet($s, $metadata['id'], imageAuthor($review))['body']; equal(str_contains($body, 'Exif'), false);
        $image = imagecreatefromstring($body); $w = imagesx($image); $h = imagesy($image);
        foreach ([[4, 4], [$w - 5, 4], [4, $h - 5], [$w - 5, $h - 5]] as $corner => [$x, $y]) {
            $pixel = imagecolorsforindex($image, imagecolorat($image, $x, $y));
            foreach (['red', 'green', 'blue'] as $channel => $name) {
                equal(abs($pixel[$name] - $colors[$expected[$corner]][$channel]) < 35, true, 'EXIF orientation ' . $orientation . ' corner ' . $corner);
            }
        }
        imagedestroy($image);
    }
};

$tests['invalid, oversized, foreign and unconsented image selections leave no assets'] = function (): void {
    [$s, $clock, $db, , $directory] = imageFixture();
    $valid = imageFile($directory);
    $invalid = $directory . '/invalid.input'; file_put_contents($invalid, '<svg xmlns="http://www.w3.org/2000/svg"><script>test</script></svg>');
    $large = $directory . '/large.input'; $stream = fopen($large, 'wb'); ftruncate($stream, ReviewImages::MAX_BYTES + 1); fclose($stream);
    $huge = $directory . '/huge.input';
    $ihdr = pack('NNCCCCC', 4000, 4000, 8, 2, 0, 0, 0);
    file_put_contents($huge, "\x89PNG\r\n\x1a\n" . pack('N', strlen($ihdr)) . 'IHDR' . $ihdr . pack('N', crc32('IHDR' . $ihdr)));
    foreach ([[[$valid, $valid, $valid, $valid], 400], [[['tmp_name' => $invalid, 'error' => 0]], 400],
        [[['tmp_name' => $large, 'error' => 0]], 413], [[['tmp_name' => $huge, 'error' => 0]], 413],
        [[['tmp_name' => '', 'error' => UPLOAD_ERR_INI_SIZE]], 413], [[['tmp_name' => '', 'error' => UPLOAD_ERR_PARTIAL]], 400]] as [$files, $status]) {
        equal(imagePost($s, 'reviews', draft($s, $clock->now), $files)['status'], $status);
        equal(imageFiles($directory), []);
    }
    $review = draft($s, $clock->now);
    equal(imagePost($s, 'reviews', array_replace($review, ['imageConsent' => false]), [$valid])['status'], 400);
    equal(imagePost($s, 'reviews', array_replace($review, ['newImageCount' => 2]), [$valid])['status'], 400);
    equal(imagePost($s, 'reviews', $review, [$valid], ['origin' => 'https://wrong.example'])['status'], 403);
    equal(imagePost($s, 'reviews', array_replace($review, ['keepImageIds' => null]), [])['status'], 400);
    equal(imageFiles($directory), []);
    equal((int) $db->query('SELECT COUNT(*) FROM wize_review_images')->fetchColumn(), 0);
    equal(imagePost($s, 'reviews', $review, [$valid])['status'], 201);
    $id = payload(manage($s, $review))['images'][0]['id'];
    $other = draft($s, $clock->now); equal(submit($s, $other)['status'], 201);
    equal(imagePost($s, 'manage', imageEdits([$id]), [], imageAuthor($other))['status'], 400);
    equal(imagePost($s, 'manage', imageEdits([$id, $id]), [], imageAuthor($review))['status'], 400);
};

$tests['published edits stage text and images together and approval removes superseded files'] = function (): void {
    [$s, $clock, , , $directory] = imageFixture();
    $review = draft($s, $clock->now); $file = imageFile($directory);
    equal(imagePost($s, 'reviews', $review, [$file, $file])['status'], 201);
    $old = payload(manage($s, $review))['images']; moderate($s, $review['id']);
    equal(imagePost($s, 'manage', imageEdits([$old[0]['id']]), [$file], imageAuthor($review))['status'], 202);
    $pending = payload(manage($s, $review))['pendingEdit']; $new = $pending['images'][1]['id'];
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['images'], $old);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['content'], $review['content']);
    equal(imageGet($s, $new)['status'], 404); equal(imageGet($s, $new, imageAuthor($review))['status'], 200);
    $queue = payload($s->handle('GET', 'moderation', ['state' => 'edits'], ['x-moderator-key' => testConfig()['moderator_key']]));
    equal($queue['reviews'][0]['images'], $pending['images']); equal($queue['reviews'][0]['originalImages'], $old);
    equal(moderate($s, $review['id'], 'approve-edit')['status'], 200);
    $public = payload($s->handle('GET', 'reviews'))['reviews'][0];
    equal($public['rating'], 2); equal($public['content'], $pending['content']); equal($public['images'], $pending['images']);
    equal(imageGet($s, $new)['status'], 200); equal(imageGet($s, $old[1]['id'])['status'], 404);
    equal(count(imageFiles($directory)), 4);
    equal(payload(manage($s, $review))['pendingEdit'], null);
    // Removing all images is itself moderated; current public images survive until approval.
    equal(imagePost($s, 'manage', imageEdits(), [], imageAuthor($review))['status'], 202);
    equal(imageGet($s, $new)['status'], 200);
    equal(moderate($s, $review['id'], 'approve-edit')['status'], 200); equal(imageFiles($directory), []);
};

$tests['rejection cancellation replacement and deletion keep only referenced image files'] = function (): void {
    [$s, $clock, , , $directory] = imageFixture();
    $review = draft($s, $clock->now); $file = imageFile($directory);
    equal(imagePost($s, 'reviews', $review, [$file])['status'], 201);
    // Unpublished changes replace the pending set immediately.
    $first = payload(manage($s, $review))['images'][0]['id'];
    equal(imagePost($s, 'manage', imageEdits(), [$file], imageAuthor($review))['status'], 200);
    equal(imageGet($s, $first, imageAuthor($review))['status'], 404); equal(count(imageFiles($directory)), 2);
    moderate($s, $review['id']); $published = payload(manage($s, $review))['images'][0]['id'];
    foreach (['reject-edit', 'cancel-edit', 'replace', 'delete'] as $action) {
        equal(imagePost($s, 'manage', imageEdits([$published]), [$file], imageAuthor($review))['status'], 202);
        $proposed = payload(manage($s, $review))['pendingEdit']['images'][1]['id'];
        if ($action === 'reject-edit') { equal(moderate($s, $review['id'], $action)['status'], 200); }
        elseif ($action === 'replace') { equal(imagePost($s, 'manage', imageEdits([$published]), [], imageAuthor($review))['status'], 202); }
        else { equal(manage($s, $review, $action)['status'], 200); }
        equal(imageGet($s, $proposed, imageAuthor($review))['status'], 404);
        equal(count(imageFiles($directory)), $action === 'delete' ? 0 : 2);
        if ($action !== 'delete') { equal(imageGet($s, $published)['status'], 200); }
    }
};

$tests['retention removes expired pending images and staged edits but preserves published assets'] = function (): void {
    [$s, $clock, , , $directory] = imageFixture(); $file = imageFile($directory);
    $pending = draft($s, $clock->now); imagePost($s, 'reviews', $pending, [$file]);
    $published = draft($s, $clock->now); imagePost($s, 'reviews', $published, [$file]); moderate($s, $published['id']);
    $publicImage = payload(manage($s, $published))['images'][0]['id'];
    imagePost($s, 'manage', imageEdits([$publicImage]), [$file], imageAuthor($published));
    equal(count(imageFiles($directory)), 6);
    $clock->now += 31 * 24 * 3600000; $s->maintenance();
    equal(manage($s, $pending)['status'], 404); equal(payload(manage($s, $published))['pendingEdit'], null);
    equal(payload(manage($s, $published))['content'], $published['content']); equal(count(imageFiles($directory)), 2);
    equal(imageGet($s, $publicImage)['status'], 200);
    equal(moderate($s, $published['id'], 'remove')['status'], 200); equal(imageFiles($directory), []);
};

$tests['failed transactions and interrupted requests do not strand or delete live files'] = function (): void {
    [$s, $clock, , , $directory] = imageFixture(['max_pending' => 0]);
    equal(imagePost($s, 'reviews', draft($s, $clock->now), [imageFile($directory)])['status'], 503);
    equal(imageFiles($directory), []);
    [$s, $clock, $db, , $directory] = imageFixture(['image_storage_limit_bytes' => ReviewImages::MAX_BYTES * 3]);
    $review = draft($s, $clock->now); $file = imageFile($directory); imagePost($s, 'reviews', $review, [$file]);
    $id = payload(manage($s, $review))['images'][0]['id'];
    $db->exec('UPDATE wize_review_images SET bytes = ' . ReviewImages::MAX_BYTES * 3);
    equal(imagePost($s, 'manage', imageEdits([$id]), [$file], imageAuthor($review))['status'], 503);
    equal(count(imageFiles($directory)), 2); equal(imageGet($s, $id, imageAuthor($review))['status'], 200);
    $old = uuid(); $recent = uuid();
    foreach ([$old, $recent] as $orphan) {
        foreach (['', '-thumb'] as $suffix) { file_put_contents($directory . '/' . $orphan . $suffix . '.webp', 'orphan'); }
    }
    foreach (['', '-thumb'] as $suffix) {
        touch($directory . '/' . $old . $suffix . '.webp', intdiv($clock->now, 1000) - 86401);
        touch($directory . '/' . $recent . $suffix . '.webp', intdiv($clock->now, 1000));
        touch($directory . '/' . $id . $suffix . '.webp', intdiv($clock->now, 1000) - 86401);
    }
    $s->maintenance(true); equal(file_exists($directory . '/' . $old . '.webp'), false);
    equal(file_exists($directory . '/' . $recent . '.webp'), true); equal(file_exists($directory . '/' . $id . '.webp'), true);
};

$tests['images are optional and private directory/schema requirements fail safely'] = function (): void {
    [$s, $clock, $db] = fixture(); equal(payload($s->handle('GET', 'challenge'))['imageLimits'], null);
    equal(submit($s, draft($s, $clock->now))['status'], 201);
    try { new ReviewImages($db, ['images_dir' => dirname(__DIR__, 2) . '/unsafe-image-test']); throw new LogicException('Public image path accepted.'); }
    catch (RuntimeException $error) { equal(str_contains($error->getMessage(), 'outside every public document root'), true); }
    $review = draft($s, $clock->now); submit($s, $review); moderate($s, $review['id']);
    $db->exec('UPDATE wize_review_guard SET schema_version = 3 WHERE id = 1');
    $db->exec(file_get_contents(dirname(__DIR__) . '/migrations/003_review_images.sql'));
    equal((int) $db->query('SELECT schema_version FROM wize_review_guard WHERE id = 1')->fetchColumn(), 4);
    equal(payload(manage(new ReviewService($db, testConfig(), static fn(): int => $clock->now), $review))['state'], 'published');
};

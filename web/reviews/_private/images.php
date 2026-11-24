<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY')) { http_response_code(404); exit; }

/** Private, normalized image assets. Authorization and review transactions belong to ReviewService. */
final class ReviewImages
{
    public const MAX_COUNT = 3;
    public const MAX_BYTES = 5242880;
    public const MAX_PIXELS = 12000000;
    public const MAX_EDGE = 8192;
    public const OUTPUT_EDGE = 2048;
    public const THUMB_EDGE = 384;
    public const MAX_REQUEST_BYTES = self::MAX_COUNT * self::MAX_BYTES + 65536;
    private ?string $directory = null;
    private int $capacity;

    public function __construct(private PDO $db, array $config)
    {
        $this->capacity = (int) ($config['image_storage_limit_bytes'] ?? 536870912);
        if ($this->capacity < self::MAX_COUNT * self::MAX_BYTES) { throw new RuntimeException('Invalid image storage capacity.'); }
        $configured = $config['images_dir'] ?? null;
        if ($configured === null || $configured === '') { return; }
        if (!is_string($configured) || !str_starts_with($configured, DIRECTORY_SEPARATOR)) {
            throw new RuntimeException('Configure an absolute private images_dir.');
        }
        $real = realpath($configured);
        if ($real === false) {
            $parent = realpath(dirname($configured));
            $name = basename($configured);
            if (!$parent || !preg_match('/^[A-Za-z0-9_-]+$/D', $name)) {
                throw new RuntimeException('Create the private image directory parent first.');
            }
            $real = $parent . DIRECTORY_SEPARATOR . $name;
        }
        foreach ([realpath(dirname(__DIR__, 2)), empty($_SERVER['DOCUMENT_ROOT']) ? false : realpath($_SERVER['DOCUMENT_ROOT'])] as $public) {
            if ($public && ($real === $public || str_starts_with($real, rtrim($public, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR))) {
                throw new RuntimeException('Image storage must be outside every public document root.');
            }
        }
        if (!is_dir($real) && !mkdir($real, 0700)) { throw new RuntimeException('Cannot create private image storage.'); }
        if (!is_writable($real)) { throw new RuntimeException('Private image storage is not writable.'); }
        $this->directory = $real;
    }

    public function limits(): ?array
    {
        if ($this->directory === null || !function_exists('imagecreatefromstring') || !function_exists('imagewebp') ||
            !function_exists('exif_read_data') || (imagetypes() & (IMG_JPG | IMG_PNG | IMG_WEBP)) !== (IMG_JPG | IMG_PNG | IMG_WEBP)) { return null; }
        return ['maxCount' => self::MAX_COUNT, 'maxBytes' => self::MAX_BYTES, 'maxPixels' => self::MAX_PIXELS,
            'maxEdge' => self::MAX_EDGE, 'outputEdge' => self::OUTPUT_EDGE, 'types' => ['image/jpeg', 'image/png', 'image/webp']];
    }

    private function path(string $id, bool $thumbnail = false): string
    {
        if (!preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/D', $id)) {
            throw new ReviewHttpError(404, 'Image unavailable.');
        }
        if ($this->directory === null) { throw new ReviewHttpError(503, 'Review images are temporarily unavailable.'); }
        return $this->directory . DIRECTORY_SEPARATOR . $id . ($thumbnail ? '-thumb' : '') . '.webp';
    }

    private function uuid(): string
    {
        $bytes = random_bytes(16);
        $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
        $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
        $hex = bin2hex($bytes);
        return substr($hex, 0, 8) . '-' . substr($hex, 8, 4) . '-' . substr($hex, 12, 4) . '-' . substr($hex, 16, 4) . '-' . substr($hex, 20);
    }

    /** Files are PHP-managed uploads in production; tests supply disposable local fixtures. */
    public function prepare(array $files): array
    {
        if ($files === []) { return []; }
        if ($this->limits() === null) { throw new ReviewHttpError(503, 'Image uploads are unavailable. You can submit text or send images by email.'); }
        if (count($files) > self::MAX_COUNT) { throw new ReviewHttpError(400, 'Attach at most three images.'); }
        $prepared = [];
        try {
            foreach ($files as $file) {
                $error = $file['error'] ?? UPLOAD_ERR_NO_FILE;
                if (in_array($error, [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true)) {
                    throw new ReviewHttpError(413, 'An image exceeds the server upload limit. Use files up to 5 MiB, or try a smaller image.');
                }
                if ($error !== UPLOAD_ERR_OK || !is_string($file['tmp_name'] ?? null) || !is_file($file['tmp_name'])) {
                    throw new ReviewHttpError(400, 'An image did not finish uploading. Please select it again.');
                }
                $size = filesize($file['tmp_name']);
                if ($size === false || $size < 1 || $size > self::MAX_BYTES) { throw new ReviewHttpError(413, 'Each image must be no larger than 5 MiB.'); }
                $info = @getimagesize($file['tmp_name']);
                if (!$info || !in_array($info[2], [IMAGETYPE_JPEG, IMAGETYPE_PNG, IMAGETYPE_WEBP], true)) {
                    throw new ReviewHttpError(400, 'Use a valid JPEG, PNG or WebP image.');
                }
                if ($info[0] < 1 || $info[1] < 1 || max($info[0], $info[1]) > self::MAX_EDGE || $info[0] * $info[1] > self::MAX_PIXELS) {
                    throw new ReviewHttpError(413, 'Each image must be at most 12 megapixels and 8192 pixels on either edge.');
                }
                $bytes = file_get_contents($file['tmp_name'], false, null, 0, self::MAX_BYTES + 1);
                $source = $bytes === false ? false : @imagecreatefromstring($bytes);
                unset($bytes);
                if (!$source) { throw new ReviewHttpError(400, 'This image cannot be decoded. Use a static JPEG, PNG or WebP image.'); }
                $id = $this->uuid();
                try {
                    if ($info[2] === IMAGETYPE_JPEG) { $source = $this->orient($source, $file['tmp_name']); }
                    $full = $this->resize($source, self::OUTPUT_EDGE);
                    try {
                        $fullBytes = $this->write($full, $this->path($id), 90);
                        $thumb = $this->resize($full, self::THUMB_EDGE);
                        try { $thumbBytes = $this->write($thumb, $this->path($id, true), 82); }
                        finally { imagedestroy($thumb); }
                        $prepared[] = ['id' => $id, 'width' => imagesx($full), 'height' => imagesy($full),
                            'bytes' => $fullBytes, 'thumbBytes' => $thumbBytes];
                    } finally { imagedestroy($full); }
                } catch (Throwable $error) {
                    $this->remove($id);
                    throw $error;
                } finally { imagedestroy($source); }
            }
            return $prepared;
        } catch (Throwable $error) {
            foreach ($prepared as $image) { $this->remove($image['id']); }
            throw $error;
        }
    }

    private function orient(GdImage $source, string $file): GdImage
    {
        $exif = @exif_read_data($file, 'IFD0', true, false);
        $orientation = (int) ($exif['IFD0']['Orientation'] ?? 1);
        if (in_array($orientation, [2, 5, 7], true)) { imageflip($source, IMG_FLIP_HORIZONTAL); }
        if ($orientation === 4) { imageflip($source, IMG_FLIP_VERTICAL); }
        $angle = match ($orientation) { 3 => 180, 5, 8 => 90, 6, 7 => -90, default => 0 };
        if ($angle !== 0) {
            $rotated = imagerotate($source, $angle, 0);
            if (!$rotated) { throw new ReviewHttpError(400, 'Image orientation could not be read.'); }
            imagedestroy($source);
            return $rotated;
        }
        return $source;
    }

    private function resize(GdImage $source, int $edge): GdImage
    {
        $scale = min(1, $edge / max(imagesx($source), imagesy($source)));
        $width = max(1, (int) round(imagesx($source) * $scale));
        $height = max(1, (int) round(imagesy($source) * $scale));
        $target = imagecreatetruecolor($width, $height);
        if (!$target) { throw new ReviewHttpError(503, 'There is not enough memory to process this image. Try a smaller image.'); }
        imagealphablending($target, false); imagesavealpha($target, true);
        imagefill($target, 0, 0, imagecolorallocatealpha($target, 0, 0, 0, 127));
        if (!imagecopyresampled($target, $source, 0, 0, 0, 0, $width, $height, imagesx($source), imagesy($source))) {
            imagedestroy($target);
            throw new ReviewHttpError(400, 'This image could not be resized.');
        }
        return $target;
    }

    private function write(GdImage $image, string $path, int $quality): int
    {
        $stream = fopen($path, 'xb');
        if (!$stream) { throw new ReviewHttpError(503, 'Image storage is temporarily unavailable.'); }
        chmod($path, 0600);
        try { $written = imagewebp($image, $stream, $quality); fflush($stream); }
        finally { fclose($stream); }
        $size = filesize($path);
        if (!$written || $size === false || $size < 1 || $size > self::MAX_BYTES) {
            throw new ReviewHttpError(413, 'The normalized image is too large. Please use a smaller image.');
        }
        return $size;
    }

    /** Must run within the service's serialized review transaction. */
    public function insert(string $reviewId, array $images, int $now): void
    {
        $added = array_sum(array_map(static fn(array $image): int => $image['bytes'] + $image['thumbBytes'], $images));
        if ($added === 0) { return; }
        if ((int) $this->db->query('SELECT COALESCE(SUM(bytes + thumb_bytes), 0) FROM wize_review_images')->fetchColumn() + $added > $this->capacity) {
            throw new ReviewHttpError(503, 'Image storage is full. Please submit text or send images by email.');
        }
        $statement = $this->db->prepare('INSERT INTO wize_review_images (id, review_id, width, height, bytes, thumb_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)');
        foreach ($images as $image) { $statement->execute([$image['id'], $reviewId, $image['width'], $image['height'], $image['bytes'], $image['thumbBytes'], $now]); }
    }

    public function list(string $reviewId, string $set = 'current'): array
    {
        $statement = $this->db->prepare('SELECT i.id, i.width, i.height, i.bytes FROM wize_review_image_links l JOIN wize_review_images i ON i.id = l.image_id WHERE l.review_id = ? AND l.image_set = ? ORDER BY l.position');
        $statement->execute([$reviewId, $set]);
        return array_map(static fn(array $image): array => ['id' => $image['id'], 'width' => (int) $image['width'],
            'height' => (int) $image['height'], 'bytes' => (int) $image['bytes']], $statement->fetchAll());
    }

    public function replaceLinks(string $reviewId, string $set, array $ids): void
    {
        $owner = $this->db->prepare('SELECT id FROM wize_review_images WHERE id = ? AND review_id = ?');
        foreach ($ids as $id) {
            $owner->execute([$id, $reviewId]);
            if (!$owner->fetchColumn()) { throw new ReviewHttpError(409, 'The images changed. Reload your review before editing.'); }
        }
        $this->db->prepare('DELETE FROM wize_review_image_links WHERE review_id = ? AND image_set = ?')->execute([$reviewId, $set]);
        $statement = $this->db->prepare('INSERT INTO wize_review_image_links (review_id, image_set, position, image_id) VALUES (?, ?, ?, ?)');
        foreach (array_values($ids) as $position => $id) { $statement->execute([$reviewId, $set, $position, $id]); }
    }

    /** Queue files transactionally before removing database rows; a rollback retains both. */
    public function queueReviewDeletion(string $reviewId): void
    {
        $this->db->prepare('INSERT IGNORE INTO wize_review_image_gc (image_id) SELECT id FROM wize_review_images WHERE review_id = ?')->execute([$reviewId]);
    }

    public function pruneUnused(string $reviewId): void
    {
        $this->db->prepare('INSERT IGNORE INTO wize_review_image_gc (image_id) SELECT i.id FROM wize_review_images i LEFT JOIN wize_review_image_links l ON l.image_id = i.id WHERE i.review_id = ? AND l.image_id IS NULL')->execute([$reviewId]);
        $this->db->prepare('DELETE i FROM wize_review_images i LEFT JOIN wize_review_image_links l ON l.image_id = i.id WHERE i.review_id = ? AND l.image_id IS NULL')->execute([$reviewId]);
    }

    public function discardUnclaimed(array $prepared): void
    {
        $statement = $this->db->prepare('SELECT id FROM wize_review_images WHERE id = ?');
        foreach ($prepared as $image) {
            $statement->execute([$image['id']]);
            if (!$statement->fetchColumn()) { $this->remove($image['id']); }
        }
    }

    public function collectGarbage(): void
    {
        if ($this->directory === null) { return; }
        $statement = $this->db->prepare('DELETE FROM wize_review_image_gc WHERE image_id = ?');
        foreach ($this->db->query('SELECT image_id FROM wize_review_image_gc LIMIT 100')->fetchAll() as $row) {
            if ($this->remove($row['image_id'])) { $statement->execute([$row['image_id']]); }
        }
    }

    /** Scheduled maintenance removes crash leftovers only after a full day, never active requests. */
    public function collectOrphans(int $now): void
    {
        if ($this->directory === null) { return; }
        $statement = $this->db->prepare('SELECT id FROM wize_review_images WHERE id = ?');
        foreach (new DirectoryIterator($this->directory) as $file) {
            if (!$file->isFile() || $file->isLink() || $file->getMTime() * 1000 > $now - 86400000 ||
                !preg_match('/^([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})(?:-thumb)?\.webp$/D', $file->getFilename(), $match)) { continue; }
            $statement->execute([$match[1]]);
            if (!$statement->fetchColumn()) { $this->remove($match[1]); }
        }
    }

    private function remove(string $id): bool
    {
        $removed = true;
        foreach ([false, true] as $thumbnail) {
            $path = $this->path($id, $thumbnail);
            if (file_exists($path) && !@unlink($path)) { $removed = false; }
        }
        return $removed;
    }

    public function read(string $id, bool $thumbnail): string
    {
        $path = $this->path($id, $thumbnail);
        if (!is_file($path) || is_link($path) || filesize($path) > self::MAX_BYTES) { throw new ReviewHttpError(404, 'Image unavailable.'); }
        $bytes = file_get_contents($path, false, null, 0, self::MAX_BYTES + 1);
        if ($bytes === false || strlen($bytes) > self::MAX_BYTES) { throw new ReviewHttpError(404, 'Image unavailable.'); }
        return $bytes;
    }
}

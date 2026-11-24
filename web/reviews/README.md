# WizeStream community reviews

This optional PHP/MySQL review system requires HTTPS and an external private `reviews-config.php`. Never publish real database credentials or moderator secrets; `_private/config.example.php` is only a template.

## Fresh installation

Import `_private/schema.sql` through phpMyAdmin (schema **v4**). Configure the private PHP file outside the document root and upload the complete `web/reviews` directory, including `.htaccess` files, generated `index.php`, both JavaScript files and CSS. The service requires PHP 8.2+ and PDO MySQL. The CI matrix runs PHP 8.2/8.4 against MySQL 8.

The private configuration is loaded from `WIZESTREAM_REVIEWS_CONFIG`, or from `wizestream-private/reviews-config.php` beside the document root. Use `_private/config.example.php` as the template. With CLI access, `php web/reviews/_private/make-config.php /absolute/private/path/reviews-config.php` creates a new file with separate random keys and a neighboring image-directory path. It never overwrites an existing config. Set its real database credentials and HTTPS `public_url` privately; do not place credentials in the repository. Preserve an existing installation's keys when upgrading.

## Upgrading a running v1 database

**Back up the MySQL database first**, then apply `_private/migrations/001_developer_replies.sql` using phpMyAdmin **before** deploying the PHP requiring schema v2. Existing review records, ratings, approval status and token hashes are not altered. Apply `_private/migrations/002_pending_review_edits.sql` after the first migration to upgrade schema v2 → v3 before deploying author-edit code. Published review edits are stored separately and require moderation. The original published rating/text and public aggregate remain unchanged until approved. Pending reviews can be edited in place; authors may cancel a pending update without deleting their review. Apply subsequent migrations in order.

## Upgrading schema v3 → v4 for images

1. Back up the dedicated review database and existing private configuration. If an image directory already exists, back it up consistently with the database. Do not regenerate reviewer or moderator keys.
2. Check `SELECT schema_version FROM wize_review_guard WHERE id = 1;`. Starting from v3, import `_private/migrations/003_review_images.sql` through phpMyAdmin. Starting from an earlier version, run migrations 001 and/or 002 first. Migration 003 creates image metadata, current/proposed links and a deletion queue; it leaves existing text, ratings, receipt hashes, developer replies and pending edits intact. It advances the guard from 3 to 4. Re-importing `schema.sql` does **not** upgrade an existing guard row.
3. Configure the private image directory, PHP extensions and limits below. Omitting `images_dir` keeps text-only reviews available; authors see that uploads are unavailable. Adding it later enables the image picker without changing existing reviews.
4. Upload the matching PHP files, `review-images.js`, `reviews.js`, CSS, generated `reviews/index.php`, privacy/support pages and other regenerated website assets together. Keep the `.htaccess` protections. Do not expose `_private` directly through another server mapping. Schema changes and file replacement should use a maintenance window: v3 PHP rejects v4, and v4 PHP rejects v3, with a safe temporary-unavailable response.
5. Verify the page, text submission and private management link, then submit a harmless image. Confirm it cannot be fetched without the author/moderator header while pending, appears publicly only after approval, and disappears after author deletion. Check a published image edit stays private until approved. Do not use real personal images for the deployment check.
6. Enable the hourly cleanup command below. Review provider backup/log retention separately; this application cannot configure or erase provider backups.

This repository has no automatic production deployment connection. Merging a PR or passing CI does not upload files, change GoDaddy PHP settings, import SQL, or deploy the site. Use the hosting account's existing cPanel/phpMyAdmin/file deployment process. A rollback needs matching PHP and schema: take a maintenance window and restore the backed-up database and private files, rather than changing only the guard number or discarding newly submitted reviews.

## Image storage and PHP setup

Enable **GD with JPEG, PNG and WebP support**, and **EXIF**. JPEG orientation is read before metadata is removed, including mirrored orientations. The service decodes the image pixels, scales to at most 2,048 pixels per edge, and writes a new WebP image plus a 384-pixel thumbnail. It does not copy original metadata, filenames or embedded payloads. Uploaded originals remain PHP temporary files and are discarded at request end. Animated images are not preserved; use still screenshots.

Use a dedicated absolute `images_dir` outside **every** served document root, for example:

```php
'images_dir' => '/home/YOUR_ACCOUNT/wizestream-private/review-images',
'image_storage_limit_bytes' => 536870912, // 512 MiB of normalized images + thumbnails
```

Create the parent private directory first. The service creates the final directory with mode `0700` when needed and writes random UUID filenames with mode `0600`. For an existing directory, use mode `0700` and ensure the PHP account can read/write it. Never alias the directory into Apache/Nginx, put it under `public_html`, or point a separate domain/subdomain at it. The code rejects paths within the PHP document root or checked-in website, including symlinks resolving there; it cannot discover unrelated domain roots configured elsewhere in the hosting account. Do not share this directory with unrelated files or services.

Set these values through the hosting PHP configuration (for example cPanel's PHP INI editor). They must apply to the web PHP process, and the cron PHP version must have the same extensions:

```ini
file_uploads = On
upload_max_filesize = 5M
post_max_size = 20M
max_file_uploads = 3
memory_limit = 256M
```

The upload contract is **3 files, 5 MiB each, 12,000,000 pixels each, no edge over 8,192 pixels**. `post_max_size` must allow the full 15 MiB payload plus multipart overhead; 20M gives headroom. Use at least **192M** memory and preferably **256M** for a 12-megapixel JPEG rotation plus resampling; files are processed sequentially. Check host request-body limits too. Configuring smaller upload/post limits does not silently drop images: the endpoint reports the host limit or an incomplete file count, preserves the existing review, and suggests smaller/fewer images or the contact route. A proxy returning its own HTML 413 response also gets a useful browser message.

Missing GD/WebP/EXIF support or an omitted image directory disables new uploads while text reviews remain available. An unsafe/unwritable configured directory is a configuration error and makes the service unavailable; fix it privately. If temporarily disabling uploads on an existing image installation, leave `images_dir` configured so authorized viewing and cleanup continue. Removing that path also makes existing image files inaccessible to the service.

The default capacity is 512 MiB across normalized full images and thumbnails, including current and proposed sets. Admission is serialized across PHP workers. A full image store rejects the attempted submission/update without altering existing content and permits text-only submissions. Increase `image_storage_limit_bytes` deliberately or moderate/delete unwanted records. Crash leftovers are not counted in this database budget until cleaned; keep the hourly task enabled and monitor the dedicated directory through the host's normal disk tools.

## Review and image lifecycle

The browser previews selected local files and uploads them only with **Submit for moderation** or **Submit changes**. Removing an unsent image or closing the page sends no deletion/upload request. A submission containing files is one multipart request with a bounded `payload` JSON field and `images[]`; `newImageCount` detects PHP silently truncating an upload list. Existing text-only JSON requests remain supported. Every upload requires image-publication consent, the same origin checks, and existing submission/management authentication. Network-derived upload attempt limits use the existing hourly HMAC buckets; no IP is attached to a review.

Pending images can be read only with `X-Review-Authorization: Review <id>:<key>` or `X-Moderator-Key`. Image URLs contain only an opaque image ID and `thumb`/`full` variant. The browser fetches private previews with these headers and displays temporary object URLs; it revokes them when cards/dialogs close or moderation locks. No owner key, moderator key, original filename or private path appears in an image URL or public metadata. All image responses use `image/webp`, `nosniff`, `no-store`, no referrer and same-origin resource policy.

An image becomes publicly accessible only when its review is published **and** it belongs to the current approved set. An author can add/remove images from a pending review immediately. Updating a published review stores its proposed text, rating and image links separately; old approved content stays visible. A single transaction approves all proposed fields and images together. Shared unchanged images are retained. Cancelling/rejecting/replacing an edit deletes only assets no longer referenced by either set. Ownership is checked both before expensive decoding and again under the write lock, so a deleted/recreated review cannot be changed with an old receipt.

Pending reviews **and pending edits** expire after 30 days. Expiring an edit preserves the published review and its images. Deleting a review (author or moderator) removes all its current/proposed assets. Database deletion records file cleanup in a transaction; physical deletion occurs after commit and retries on subsequent requests or scheduled maintenance if the filesystem was temporarily unavailable. A failed submission rolls back the database and removes prepared files. A crashed process can leave unclaimed files, which scheduled maintenance removes once they are over 24 hours old. Only recognized UUID WebP names are eligible; live referenced assets are never treated as orphans.

Configure an hourly job using the correct PHP binary and absolute paths:

```sh
WIZESTREAM_REVIEWS_CONFIG=/home/YOUR_ACCOUNT/wizestream-private/reviews-config.php /usr/local/bin/php /home/YOUR_ACCOUNT/public_html/reviews/_private/cleanup.php
```

The command is CLI-only. It expires challenges, rate buckets, pending reviews and edits, retries the committed deletion queue, and scans for orphan image files. Normal HTTP requests perform expiry and bounded queued-file cleanup but do not scan the directory. Review backups must include the private image directory as well as MySQL; author deletion cannot recall images already downloaded by someone else or remove operator backups.

## Moderation

Unlock `?view=moderate` with the existing moderator key. Publish, update or remove one official developer reply per published review. Replies cannot change the reviewer's rating or content. The public API exposes no management token, email, IP or device ID, and the client renders feedback as text rather than HTML.

Review every attached thumbnail at full size before approval. Pending edits show proposed images alongside the current public images. Apply the same policy to text and images: spam, abuse, unrelated content, duplicates or exposed personal information can be removed; criticism or a low rating is not a removal reason. Authors can correct a pending review using their existing receipt. Approval publishes the whole proposed set; there is no independent unmoderated image upload route.

The visible contact block uses the project's existing `wizdom13@gmail.com` error-report address plus GitHub bug/feature templates. Email opens the visitor's mail app; the website neither sends mail nor turns email into a review. GitHub reports are public and require an account. Neither route is needed to submit an anonymous review.

Run the review tests with the disposable MySQL database in `.github/workflows/reviews.yml`. Run `python3 web/build.py` and `python3 web/check.py` before deployment. A PR does not deploy the site.

The PHP suite covers real image decoding, all eight JPEG EXIF orientations, byte/pixel limits, receipt/moderator isolation, pending/public access, published-edit approval, rejection/cancellation/replacement, deletion, expiry, orphan cleanup, capacity rollback, migration preservation and a review-owner replacement race. HTTP checks send real multipart bodies and binary image requests, including undersized PHP upload/post limits and truncated file counts. Node tests cover browser-local selection/removal, format/size checks, header-only authorization and multipart transport. No test connects to production: the harness requires the disposable database name `wizestream_reviews_test`.

## Optional remembered review access

**Remember my review** is unchecked by default. Only an explicit opt-in stores the review's ID, private 43-character management key and local save date in that browser's localStorage. The My Reviews page uses those browser-local records only; it cannot list or recover arbitrary anonymous reviews from the server. A private link can also be remembered later from its management page. The credential never appears in the public API, local nickname lists, cookies, analytics, or third-party scripts. A browser reset or a new device cannot recover keys that were not saved elsewhere.

Anyone using the same browser profile may edit or delete a remembered review. Do not enable it on shared devices. "Forget" clears local access but does **not** delete the review; deleting an owned review removes its remembered key. When storage is disabled or full, users must save the original private link manually.

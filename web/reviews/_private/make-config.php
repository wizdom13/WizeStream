<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
if ($argc !== 2) { fwrite(STDERR, "Usage: php make-config.php /private/path/reviews-config.php\n"); exit(1); }
$destination = $argv[1];
$parent = realpath(dirname($destination));
$web = (string) realpath(dirname(__DIR__, 2));
if (!$parent || str_starts_with($parent . '/', $web . '/')) { fwrite(STDERR, "Choose an existing private directory outside the website.\n"); exit(1); }
$random = static fn(): string => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
define('WIZE_REVIEWS_ENTRY', true);
$config = require __DIR__ . '/config.example.php';
foreach (['moderator_key', 'challenge_key', 'rate_key'] as $name) { $config[$name] = $random(); }
umask(0077);
$file = fopen($destination, 'x');
if (!$file) { fwrite(STDERR, "The configuration already exists or cannot be created.\n"); exit(1); }
fwrite($file, "<?php\n// Private server configuration. Keep outside all document roots.\nreturn " . var_export($config, true) . ";\n");
fclose($file);
echo "Private configuration created. Edit its database settings, then save its moderator_key in your password manager.\n";

<?php
// Copy outside public_html as /home/YOUR_ACCOUNT/wizestream-private/reviews-config.php.
// Never put real credentials in the repository or any publicly served directory.
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY')) { http_response_code(404); exit; }
return [
    'public_url' => 'https://wizestream.org/reviews/',
    'database' => [
        'host' => 'localhost',
        'port' => 3306,
        'name' => 'YOUR_CPANEL_DATABASE',
        'username' => 'YOUR_CPANEL_DATABASE_USER',
        'password' => 'REPLACE_DATABASE_PASSWORD',
    ],
    // Generate three independent random URL-safe secrets (43–128 characters each).
    // For example: use make-config.php locally/cPanel Terminal or a password manager.
    'moderator_key' => 'REPLACE_WITH_A_RANDOM_PRIVATE_MODERATOR_KEY_43_CHARACTERS_OR_MORE',
    'challenge_key' => 'REPLACE_WITH_A_DIFFERENT_RANDOM_SECRET_43_CHARACTERS_OR_MORE',
    'rate_key' => 'REPLACE_WITH_ANOTHER_RANDOM_SECRET_43_CHARACTERS_OR_MORE',
];

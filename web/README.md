# WizeStream website

A responsive, multipage static product website and handbook. All website files live in `web/`; the Android app does not depend on them.

## What is included

- Homepage, feature overview, downloads, screenshots, comparison, FAQ, privacy, support, credits and release availability pages.
- Fifteen detailed guides covering installation, services, playback, SponsorBlock, subscriptions, downloads, profiles/sync, migration, learning, themes, devices, live/local media, privacy and troubleshooting.
- A comparison of 35 features across WizeStream, NewPipe, PipePipe, LibreTube and historical Tubular, with official references and explicit unknowns.
- Local full-text search, guide/FAQ filters, project/category/feature comparison filters, a mobile navigation menu and light/dark appearance.
- Real project screenshots and logo served locally. No analytics, remote fonts, third-party scripts, package manager, API key or backend is needed.

The checked-in HTML is the deployment output. Navigation, article text, the comparison table and FAQ work without JavaScript. JavaScript enhances search, filtering, navigation and the optional saved color preference. All links are relative, so the site supports a domain root or a project subdirectory.

## Build and check

Python 3.10+ is sufficient for the build and structural checker; there are no Python dependencies.

```sh
python3 web/build.py
python3 web/check.py
```

Also check JavaScript syntax with Node.js when available:

```sh
node --check web/assets/site.js
node --check web/assets/search-index.js
```

For a local browser preview, run the standard-library server from the repository root:

```sh
python3 -m http.server 8000 --directory web
```

Open `http://localhost:8000/`. Check narrow phone layouts, desktop layouts, both themes, keyboard navigation, the search dialog and all filters. The structural checker is useful for regressions; it is not a substitute for browser, screen-reader or device testing.

## Edit the source, then rebuild

| File | Purpose |
| --- | --- |
| `build.py` | Shared templates, page copy and a small, deliberately limited Markdown renderer |
| `content/site.json` | Release/snapshot metadata, guide catalog and sourced comparison data |
| `content/docs/*.md` | Guide text; headings begin at `##` because the template supplies the page title |
| `assets/site.css` | Responsive layout, themes, reduced-motion and print styles |
| `assets/site.js` | Browser-only enhancement behavior |
| `assets/images/` | Locally served project graphics |
| `check.py` | Internal links/anchors, markup structure, metadata, search and comparison checks |

`build-manifest.json`, `assets/search-index.js`, `robots.txt` and all HTML pages are generated. Commit regenerated files with their source changes. Do not edit only a generated page.

The guide Markdown subset supports paragraphs, `##`–`####` headings, flat lists, tables, fenced code, inline code, bold text and links. It intentionally does not support arbitrary HTML or executable content. Guide links are relative to the resulting `docs/*.html` page. Use `.html` links, not `.md`, for other website guides.

## Keep factual and release context current

The initial content snapshot is reviewed on 13 November 2026, with stable 1.41.0 listed separately from the reviewed development branch. In particular, FreeTube transfer, SponsorBlock contributions/disclosures and independent off-by-default 360°/A–B switches are marked as newer development work. Do not remove those labels merely because a change is merged.

When a release ships, update `content/site.json` and release-specific text/links in `build.py` and the affected guides. The primary download button uses GitHub's latest-release route; explicit architecture-specific APK links intentionally name their release. Review both. Update the displayed review date, pinned source commit, competitor versions, feature evidence and availability labels together when doing a new factual review.

The comparison describes selected capabilities rather than declaring an overall winner. Preserve competitors' verified strengths. `unknown` means not established by the reviewed official sources, not a proven absence. The “Differences only” filter compares capability status among visible projects; feature notes can still differ when statuses match. Archived Tubular is explicitly historical. Refer to the linked tagged docs/source files before changing a cell.

## Deploy

Serve the **contents of `web/`** from any ordinary static host, including your own HTTPS web server. No build has to run on the server. There is no client-side routing fallback: direct requests for `docs/playback.html`, for example, should return that file.

An actual domain is deliberately not assumed. Once hosting is known, generate canonical/Open Graph URLs and a sitemap using the real HTTPS base URL, including the project path when applicable:

```sh
python3 web/build.py --base-url https://your-real-host.example/wizestream/
python3 web/check.py
```

Replace the example with the actual public URL before deployment. Running the build again without `--base-url` removes canonical URLs and the generated sitemap. By default there is no sitemap with a fictitious origin.

For a deployment artifact, include the generated `*.html` files, `docs/*.html`, `assets/`, `robots.txt` and `sitemap.xml` if generated. The Python/Markdown/JSON source can stay in the repository; it is public and contains no secrets. Hosting the full `web/` directory also works. This change does not activate any hosting service or publish a domain.

Recommended host configuration: HTTPS, correct HTML/CSS/JavaScript/SVG/PNG MIME types, short cache lifetimes or revalidation for HTML and the unversioned CSS/JS, and `X-Content-Type-Options: nosniff`. Avoid permanent immutable caching of assets with unchanged filenames. If your host supports headers, a compatible policy is:

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'none'
Referrer-Policy: no-referrer
```

The optional appearance preference uses `localStorage` key `wizestream-site-theme`. Search/filter input is not sent anywhere. No service worker or tracking cookie is installed. The website privacy page leaves hosting logs to the actual hosting configuration rather than promising a server policy that has not been configured.

## Graphics and attribution

`logo.svg` is copied from `assets/wizestream_logo_round.svg`, contributed to WizeStream by Fabián PS (@FabianOvrWrt). The four PNGs are copied from the English Fastlane phone screenshots (`08`, `05`, `06`, `07`). They are real repository assets, not newly invented screens. Their captions explicitly explain that theme, version and device can change the appearance. Preserve the repository's GPLv3 licensing and attribution; see the site About page and root license/component notices.

Keep the site free of invented download totals, testimonials, ratings or live review claims. A separate review service is not assumed to be deployed or embedded here.

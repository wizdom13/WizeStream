from pathlib import Path

fragment_path = Path('app/src/main/java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java')
source = fragment_path.read_text()

old = '''            view.post(() -> {
                if (binding != null && isPlayerAvailable() && getView() != null) {
                    setHeightThumbnail();
                }
            });
'''
new = '''            view.post(() -> {
                if (binding != null && isPlayerAvailable() && getView() != null) {
                    syncFullscreenWithCurrentViewport();
                    setHeightThumbnail();
                }
            });
'''
if source.count(old) != 1:
    raise SystemExit(f'Expected one root resize reconciliation block, found {source.count(old)}')
source = source.replace(old, new, 1)

old = '''        binding.getRoot().post(() -> {
            if (binding != null && player != null && isAdded()) {
                syncFullscreenWithOrientation(
                        player.UIs().get(MainPlayerUi.class), orientation);
            }
        });
'''
new = '''        binding.getRoot().post(() -> {
            if (binding != null && player != null && isAdded()) {
                syncFullscreenWithCurrentViewport();
            }
        });
'''
if source.count(old) != 1:
    raise SystemExit(f'Expected one posted configuration sync block, found {source.count(old)}')
source = source.replace(old, new, 1)

marker = '''    private void syncFullscreenWithOrientation(
            @NonNull final Optional<MainPlayerUi> playerUi,
            final int orientation) {
'''
helper = '''    /**
     * Reconciles the logical fullscreen state from the viewport that is actually laid out.
     * Configuration callbacks can arrive before or after the final rotation layout, so the
     * viewport is the authoritative source once it has non-zero dimensions.
     */
    private void syncFullscreenWithCurrentViewport() {
        if (binding == null || player == null || getView() == null) {
            return;
        }
        final View root = requireView();
        final int orientation = orientationFromViewportDimensions(
                root.getWidth(),
                root.getHeight(),
                getResources().getConfiguration().orientation);
        syncFullscreenWithOrientation(player.UIs().get(MainPlayerUi.class), orientation);
    }

'''
if source.count(marker) != 1:
    raise SystemExit(f'Expected one fullscreen sync overload marker, found {source.count(marker)}')
source = source.replace(marker, helper + marker, 1)
fragment_path.write_text(source)

test_path = Path('app/src/test/java/org/schabi/newpipe/fragments/detail/VideoDetailOrientationHandlingTest.java')
test = test_path.read_text()
old = '''        assertTrue(fragment.contains("binding.getRoot().addOnLayoutChangeListener"));
        assertTrue(fragment.contains(
                "isFullscreenForCurrentOrientation(viewportWidth, viewportHeight)"));
        assertTrue(fragment.contains("root.getWidth() > 0 ? root.getWidth()"));
        assertTrue(fragment.contains("root.getHeight() > 0 ? root.getHeight()"));
'''
new = '''        assertTrue(fragment.contains("binding.getRoot().addOnLayoutChangeListener"));
        assertTrue(fragment.contains("syncFullscreenWithCurrentViewport();"));
        assertTrue(fragment.contains(
                "private void syncFullscreenWithCurrentViewport()"));
        assertTrue(fragment.contains(
                "isFullscreenForCurrentOrientation(viewportWidth, viewportHeight)"));
        assertTrue(fragment.contains("root.getWidth() > 0 ? root.getWidth()"));
        assertTrue(fragment.contains("root.getHeight() > 0 ? root.getHeight()"));
'''
if test.count(old) != 1:
    raise SystemExit(f'Expected one viewport test assertion block, found {test.count(old)}')
test_path.write_text(test.replace(old, new, 1))

Path('.github/patch_pr387_fullscreen_state.py').unlink()
Path('.github/workflows/temporary-pr387-fullscreen-state.yml').unlink()

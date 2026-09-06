package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.schabi.newpipe.R;

public class CustomCollapsingToolbarLayout extends CollapsingToolbarLayout {
    @Nullable
    private ViewGroup playerPlaceholder;
    @Nullable
    private View playerLayer;

    public CustomCollapsingToolbarLayout(@NonNull final Context context) {
        super(context);
        overrideListener();
    }

    public CustomCollapsingToolbarLayout(@NonNull final Context context,
                                         @Nullable final AttributeSet attrs) {
        super(context, attrs);
        overrideListener();
    }

    public CustomCollapsingToolbarLayout(@NonNull final Context context,
                                         @Nullable final AttributeSet attrs,
                                         final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        overrideListener();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final View placeholder = findViewById(R.id.player_placeholder);
        if (!(placeholder instanceof ViewGroup)) {
            return;
        }

        playerPlaceholder = (ViewGroup) placeholder;
        playerLayer = findDirectChildContaining(placeholder);
        playerPlaceholder.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(final View parent, final View child) {
                updatePlayerLayerZOrder();
            }

            @Override
            public void onChildViewRemoved(final View parent, final View child) {
                updatePlayerLayerZOrder();
            }
        });
        updatePlayerLayerZOrder();
    }

    @Nullable
    private View findDirectChildContaining(@NonNull final View descendant) {
        View current = descendant;
        while (current.getParent() instanceof View && current.getParent() != this) {
            current = (View) current.getParent();
        }
        return current.getParent() == this ? current : null;
    }

    private void updatePlayerLayerZOrder() {
        if (playerPlaceholder == null || playerLayer == null) {
            return;
        }

        // Keep ordinary thumbnails/artwork in the normal collapsing-toolbar stack so song details
        // can draw above them. Once the actual video player is attached, restore the one-dp layer
        // lift that keeps scrolling title/uploader content from being painted over the video.
        final float desiredTranslationZ = playerPlaceholder.getChildCount() > 0
                ? getResources().getDisplayMetrics().density : 0.0f;
        if (Float.compare(playerLayer.getTranslationZ(), desiredTranslationZ) != 0) {
            playerLayer.setTranslationZ(desiredTranslationZ);
        }
    }

    /**
     * CollapsingToolbarLayout sets it's own setOnApplyInsetsListener which consumes
     * system insets {@link CollapsingToolbarLayout#onWindowInsetChanged(WindowInsetsCompat)}
     * so we will not receive them in subviews with fitsSystemWindows = true.
     * Override Google's behavior
     * */
    public void overrideListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> insets);
    }
}

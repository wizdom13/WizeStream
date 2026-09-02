package org.schabi.newpipe.views;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.pip.NativePipController;
import org.schabi.newpipe.util.NewPipeTextViewHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

/**
 * An {@link AppCompatTextView} which uses {@link ShareUtils#shareText(Context, String, String)}
 * when sharing selected text by using the {@code Share} command of the floating actions.
 *
 * <p>
 * This class allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing
 * text from {@link AppCompatTextView} on EMUI devices and also to keep movement method set when a
 * text change occurs, if the text cannot be selected and text links are clickable.
 * </p>
 */
public class NewPipeTextView extends AppCompatTextView {
    @Nullable
    private OnClickListener legacyPopupClickListener;
    @Nullable
    private OnLongClickListener legacyPopupLongClickListener;
    @Nullable
    private OnTouchListener legacyPopupTouchListener;

    public NewPipeTextView(@NonNull final Context context) {
        super(context);
    }

    public NewPipeTextView(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public NewPipeTextView(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(final CharSequence text, final BufferType type) {
        // We need to set again the movement method after a text change because Android resets the
        // movement method to the default one in the case where the text cannot be selected and
        // text links are clickable (which is the default case in NewPipe).
        final MovementMethod movementMethod = this.getMovementMethod();
        super.setText(text, type);
        setMovementMethod(movementMethod);
    }

    @Override
    public void setOnClickListener(@Nullable final OnClickListener listener) {
        if (!isPrimaryDetailPipAction()) {
            super.setOnClickListener(listener);
            return;
        }

        legacyPopupClickListener = listener;
        setText(R.string.controls_pip_title);
        setContentDescription(getContext().getString(R.string.enter_picture_in_picture));
        super.setOnClickListener(view -> {
            if (!enterNativePictureInPicture() && legacyPopupClickListener != null) {
                legacyPopupClickListener.onClick(view);
            }
        });
        post(this::ensureLegacyPopupAction);
    }

    @Override
    public void setOnLongClickListener(@Nullable final OnLongClickListener listener) {
        if (!isPrimaryDetailPipAction()) {
            super.setOnLongClickListener(listener);
            return;
        }

        legacyPopupLongClickListener = listener;
        super.setOnLongClickListener(null);
        post(this::ensureLegacyPopupAction);
    }

    @Override
    public void setOnTouchListener(@Nullable final OnTouchListener listener) {
        if (!isPrimaryDetailPipAction()) {
            super.setOnTouchListener(listener);
            return;
        }

        legacyPopupTouchListener = listener;
        super.setOnTouchListener(null);
        post(this::ensureLegacyPopupAction);
    }

    @Override
    public void setVisibility(final int visibility) {
        super.setVisibility(visibility);
        if (isPrimaryDetailPipAction()) {
            post(this::syncLegacyPopupVisibility);
        }
    }

    private boolean isPrimaryDetailPipAction() {
        return getId() == R.id.detail_controls_popup;
    }

    private boolean enterNativePictureInPicture() {
        Context current = getContext();
        while (current instanceof ContextWrapper) {
            if (current instanceof AppCompatActivity) {
                return new NativePipController((AppCompatActivity) current)
                        .enterPictureInPicture();
            }
            final Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return false;
    }

    private void ensureLegacyPopupAction() {
        if (!isPrimaryDetailPipAction()) {
            return;
        }
        final View root = getRootView();
        final LinearLayout secondaryControls =
                root.findViewById(R.id.detail_secondary_control_panel);
        if (secondaryControls == null) {
            return;
        }

        View legacyPopup = root.findViewById(R.id.detail_controls_legacy_popup);
        if (legacyPopup == null) {
            legacyPopup = LayoutInflater.from(getContext()).inflate(
                    R.layout.detail_legacy_popup_action, secondaryControls, false);
            secondaryControls.addView(legacyPopup, 0);
        }
        legacyPopup.setOnClickListener(legacyPopupClickListener);
        legacyPopup.setOnLongClickListener(legacyPopupLongClickListener);
        legacyPopup.setOnTouchListener(legacyPopupTouchListener);
        legacyPopup.setVisibility(getVisibility());
        if (getVisibility() == View.VISIBLE) {
            final View secondaryToggle = root.findViewById(
                    R.id.detail_toggle_secondary_controls_view);
            if (secondaryToggle != null) {
                secondaryToggle.setVisibility(View.VISIBLE);
            }
        }
    }

    private void syncLegacyPopupVisibility() {
        final View root = getRootView();
        final View legacyPopup = root.findViewById(R.id.detail_controls_legacy_popup);
        if (legacyPopup != null) {
            legacyPopup.setVisibility(getVisibility());
        }
        if (getVisibility() == View.VISIBLE) {
            final View secondaryToggle = root.findViewById(
                    R.id.detail_toggle_secondary_controls_view);
            if (secondaryToggle != null) {
                secondaryToggle.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onTextContextMenuItem(final int id) {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this);
            return true;
        }
        return super.onTextContextMenuItem(id);
    }
}

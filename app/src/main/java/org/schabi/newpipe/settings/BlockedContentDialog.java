package org.schabi.newpipe.settings;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ContentBlockingHelper.Entry;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class BlockedContentDialog<T> {
    private final Context context;
    private final SearchableBlocklist<T> model;
    private final EditText search;
    private final TextView empty;
    private final RowsAdapter adapter = new RowsAdapter();
    private List<Integer> visible;
    private final AlertDialog dialog;

    static AlertDialog showEntries(final Context context, final boolean videos,
                                  final List<Entry> entries, final Consumer<List<Entry>> save) {
        return new BlockedContentDialog<>(context,
                videos ? R.string.manage_blocked_videos_title
                        : R.string.manage_blocked_channels_title,
                new SearchableBlocklist<>(entries, Entry::getLabel,
                        entry -> entry.getLabel() + "\n" + entry.getKey()), save, null).show();
    }

    static AlertDialog showKeywords(final Context context, final String keywords,
                                   final Consumer<String> save) {
        return new BlockedContentDialog<>(context, R.string.blocked_keywords_title,
                new SearchableBlocklist<>(keywords(keywords), value -> value, value -> value),
                selected -> save.accept(ContentBlockingHelper.sanitizeKeywords(
                        String.join("\n", selected))),
                model -> editKeywords(context, model)).show();
    }

    private BlockedContentDialog(final Context context, final int title,
                                 final SearchableBlocklist<T> model,
                                 final Consumer<List<T>> save,
                                 @Nullable final Function<SearchableBlocklist<T>, AlertDialog>
                                         edit) {
        this.context = context;
        this.model = model;
        visible = model.matchingIndices("");
        final View content = LayoutInflater.from(context)
                .inflate(R.layout.dialog_blocked_content, null);
        search = content.findViewById(R.id.blocked_content_search);
        empty = content.findViewById(R.id.blocked_content_no_results);
        final ListView list = content.findViewById(R.id.blocked_content_list);
        final int heightDp = Math.max(80,
                Math.min(300, context.getResources().getConfiguration().screenHeightDp - 240));
        list.getLayoutParams().height = (int) (heightDp
                * context.getResources().getDisplayMetrics().density);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            model.toggle(visible.get(position));
            adapter.notifyDataSetChanged();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence text, final int start,
                                          final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence text, final int start,
                                      final int before, final int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(final Editable text) {
            }
        });
        dialog = new MaterialAlertDialogBuilder(context).setTitle(title).setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok,
                        (ignored, which) -> save.accept(model.selectedValues()))
                .setNeutralButton(edit == null ? R.string.blocked_content_clear_all
                        : R.string.blocked_keywords_edit, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                if (edit == null) {
                    save.accept(List.of());
                    dialog.dismiss();
                } else {
                    edit.apply(model).setOnDismissListener(closed -> refresh());
                }
            });
        });
        refresh();
    }

    private AlertDialog show() {
        dialog.show();
        return dialog;
    }

    private void refresh() {
        visible = model.matchingIndices(search.getText().toString());
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private static List<String> keywords(final String text) {
        final String sanitized = ContentBlockingHelper.sanitizeKeywords(text);
        return sanitized.isEmpty() ? List.of() : Arrays.asList(sanitized.split("\n"));
    }

    private static AlertDialog editKeywords(final Context context,
                                     final SearchableBlocklist<String> model) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        input.setText(String.join("\n", model.selectedValues()));
        return new MaterialAlertDialogBuilder(context).setTitle(R.string.blocked_keywords_title)
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (ignored, which) -> {
                    model.replace(keywords(input.getText().toString()));
                }).show();
    }

    private final class RowsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public Integer getItem(final int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position);
        }

        @Override
        public View getView(final int position, final View recycled, final ViewGroup parent) {
            final CheckedTextView row = recycled instanceof CheckedTextView
                    ? (CheckedTextView) recycled
                    : (CheckedTextView) LayoutInflater.from(context).inflate(
                            android.R.layout.simple_list_item_multiple_choice, parent, false);
            final int index = getItem(position);
            row.setText(model.label(index));
            row.setChecked(model.isChecked(index));
            return row;
        }
    }
}

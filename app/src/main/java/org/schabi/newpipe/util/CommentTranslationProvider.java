package org.schabi.newpipe.util;

import android.content.Context;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextLanguage;
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommentTranslationProvider {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    public enum Failure {
        LANGUAGE_UNDETECTED,
        ALREADY_TARGET_LANGUAGE,
        ON_DEVICE_UNAVAILABLE,
        TRANSLATION_FAILED
    }

    public interface Callback {
        void onSuccess(@NonNull String translatedText);

        void onFailure(@NonNull Failure failure);
    }

    private CommentTranslationProvider() {
    }

    public static boolean isAvailableOnPlatform() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void translate(@NonNull final Context context,
                                 @NonNull final String text,
                                 @NonNull final Callback callback) {
        if (!isAvailableOnPlatform()) {
            callback.onFailure(Failure.ON_DEVICE_UNAVAILABLE);
            return;
        }
        if (text.isBlank()) {
            callback.onFailure(Failure.LANGUAGE_UNDETECTED);
            return;
        }

        final Context applicationContext = context.getApplicationContext();
        WORKER.execute(() -> Api31Impl.translate(applicationContext, text, callback));
    }

    private static void deliverSuccess(@NonNull final Callback callback,
                                       @NonNull final String translatedText) {
        MAIN_HANDLER.post(() -> callback.onSuccess(translatedText));
    }

    private static void deliverFailure(@NonNull final Callback callback,
                                       @NonNull final Failure failure) {
        MAIN_HANDLER.post(() -> callback.onFailure(failure));
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31Impl {
        private Api31Impl() {
        }

        static void translate(@NonNull final Context context,
                              @NonNull final String text,
                              @NonNull final Callback callback) {
            try {
                final ULocale sourceLocale = detectSourceLocale(context, text);
                if (sourceLocale == null) {
                    deliverFailure(callback, Failure.LANGUAGE_UNDETECTED);
                    return;
                }

                final ULocale targetLocale = ULocale.forLocale(
                        context.getResources().getConfiguration().getLocales().get(0));
                if (CommentTranslationPolicy.isSameLanguage(
                        sourceLocale.toLanguageTag(), targetLocale.toLanguageTag())) {
                    deliverFailure(callback, Failure.ALREADY_TARGET_LANGUAGE);
                    return;
                }

                final TranslationManager manager =
                        context.getSystemService(TranslationManager.class);
                if (manager == null) {
                    deliverFailure(callback, Failure.ON_DEVICE_UNAVAILABLE);
                    return;
                }

                final TranslationCapability capability =
                        findOnDeviceCapability(manager, sourceLocale, targetLocale);
                if (capability == null) {
                    deliverFailure(callback, Failure.ON_DEVICE_UNAVAILABLE);
                    return;
                }

                final TranslationContext translationContext =
                        new TranslationContext.Builder(
                                capability.getSourceSpec(),
                                capability.getTargetSpec())
                                .build();

                manager.createOnDeviceTranslator(
                        translationContext,
                        WORKER,
                        translator -> translateWithSession(translator, text, callback));
            } catch (final RuntimeException e) {
                deliverFailure(callback, Failure.TRANSLATION_FAILED);
            }
        }

        private static ULocale detectSourceLocale(@NonNull final Context context,
                                                   @NonNull final String text) {
            final TextClassificationManager manager =
                    context.getSystemService(TextClassificationManager.class);
            if (manager == null) {
                return null;
            }

            final TextLanguage language = manager.getTextClassifier().detectLanguage(
                    new TextLanguage.Request.Builder(text).build());
            return language.getLocaleHypothesisCount() > 0 ? language.getLocale(0) : null;
        }

        private static TranslationCapability findOnDeviceCapability(
                @NonNull final TranslationManager manager,
                @NonNull final ULocale sourceLocale,
                @NonNull final ULocale targetLocale) {
            final Set<TranslationCapability> capabilities =
                    manager.getOnDeviceTranslationCapabilities(
                            TranslationSpec.DATA_FORMAT_TEXT,
                            TranslationSpec.DATA_FORMAT_TEXT);
            for (final TranslationCapability capability : capabilities) {
                if (capability.getState() != TranslationCapability.STATE_ON_DEVICE) {
                    continue;
                }
                if (CommentTranslationPolicy.capabilityMatches(
                        capability.getSourceSpec().getLocale().toLanguageTag(),
                        sourceLocale.toLanguageTag())
                        && CommentTranslationPolicy.capabilityMatches(
                        capability.getTargetSpec().getLocale().toLanguageTag(),
                        targetLocale.toLanguageTag())) {
                    return capability;
                }
            }
            return null;
        }

        private static void translateWithSession(final Translator translator,
                                                 @NonNull final String text,
                                                 @NonNull final Callback callback) {
            if (translator == null) {
                deliverFailure(callback, Failure.ON_DEVICE_UNAVAILABLE);
                return;
            }

            final TranslationRequest request = new TranslationRequest.Builder()
                    .setTranslationRequestValues(Collections.singletonList(
                            TranslationRequestValue.forText(text)))
                    .build();

            try {
                translator.translate(request, null, WORKER, response -> {
                    try {
                        if (response.getTranslationStatus()
                                != TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                            deliverFailure(callback, Failure.TRANSLATION_FAILED);
                            return;
                        }

                        final TranslationResponseValue value =
                                response.getTranslationResponseValues().get(0);
                        if (value == null
                                || value.getStatusCode()
                                != TranslationResponseValue.STATUS_SUCCESS
                                || value.getText() == null
                                || value.getText().toString().isBlank()) {
                            deliverFailure(callback, Failure.TRANSLATION_FAILED);
                            return;
                        }

                        deliverSuccess(callback, value.getText().toString());
                    } finally {
                        translator.destroy();
                    }
                });
            } catch (final RuntimeException e) {
                translator.destroy();
                deliverFailure(callback, Failure.TRANSLATION_FAILED);
            }
        }
    }
}

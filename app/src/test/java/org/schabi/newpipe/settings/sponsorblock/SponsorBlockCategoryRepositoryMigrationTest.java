package org.schabi.newpipe.settings.sponsorblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SponsorBlockCategoryRepositoryMigrationTest {
    @Test
    public void migrationIsOneShotAndDoesNotMaterializeDefaults() {
        final InMemoryPreferences preferences = new InMemoryPreferences();

        SponsorBlockCategoryRepository.migrateBehaviorOnce(preferences);
        SponsorBlockCategoryRepository.migrateBehaviorOnce(preferences);

        assertTrue(preferences.getBoolean(SponsorBlockCategoryRepository.MIGRATION_KEY, false));
        assertFalse(preferences.contains(SponsorBlockCategoryConfig.SPONSOR.behaviorKey()));
    }

    @Test
    public void migrationPreservesExistingOverrideAndLaterChoice() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        preferences.edit()
                .putString(SponsorBlockCategoryConfig.SPONSOR.behaviorKey(),
                        SponsorBlockBehavior.MANUAL.value)
                .apply();

        SponsorBlockCategoryRepository.migrateBehaviorOnce(preferences);
        preferences.edit()
                .putString(SponsorBlockCategoryConfig.SPONSOR.behaviorKey(),
                        SponsorBlockBehavior.DONT_SKIP.value)
                .apply();
        SponsorBlockCategoryRepository.migrateBehaviorOnce(preferences);

        assertEquals(SponsorBlockBehavior.DONT_SKIP.value,
                preferences.getString(SponsorBlockCategoryConfig.SPONSOR.behaviorKey(), null));
    }

    @Test
    public void migrationRemovesHighlightOverride() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        preferences.edit()
                .putString(SponsorBlockCategoryConfig.HIGHLIGHT.behaviorKey(),
                        SponsorBlockBehavior.SKIP.value)
                .apply();

        SponsorBlockCategoryRepository.migrateBehaviorOnce(preferences);

        assertFalse(preferences.contains(SponsorBlockCategoryConfig.HIGHLIGHT.behaviorKey()));
        assertEquals(SponsorBlockBehavior.DONT_SKIP,
                SponsorBlockCategoryConfig.HIGHLIGHT.defaultBehavior);
    }


    @Test
    public void setAllEnabledTrueEnablesEveryCategory() {
        final InMemoryPreferences preferences = new InMemoryPreferences();

        SponsorBlockCategoryRepository.setAllEnabled(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey, true);

        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            assertTrue(SponsorBlockCategoryRepository.isEnabled(preferences, category,
                    SponsorBlockCategoryRepositoryMigrationTest::enabledKey));
        }
    }

    @Test
    public void setAllEnabledFalseDisablesEveryCategory() {
        final InMemoryPreferences preferences = new InMemoryPreferences();

        SponsorBlockCategoryRepository.setAllEnabled(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey, false);

        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            assertFalse(SponsorBlockCategoryRepository.isEnabled(preferences, category,
                    SponsorBlockCategoryRepositoryMigrationTest::enabledKey));
        }
    }

    @Test
    public void bulkEnabledChangesPreserveColorAndBehavior() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        SponsorBlockCategoryRepository.setColor(preferences, SponsorBlockCategoryConfig.SPONSOR,
                0xFF123456);
        SponsorBlockCategoryRepository.setBehavior(preferences, SponsorBlockCategoryConfig.SPONSOR,
                SponsorBlockBehavior.MANUAL);

        SponsorBlockCategoryRepository.setAllEnabled(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey, false);
        SponsorBlockCategoryRepository.setAllEnabled(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey, true);

        assertEquals(0xFF123456, SponsorBlockCategoryRepository.getColor(preferences,
                SponsorBlockCategoryConfig.SPONSOR, 0xFF000000));
        assertEquals(SponsorBlockBehavior.MANUAL,
                SponsorBlockCategoryRepository.getBehavior(preferences,
                        SponsorBlockCategoryConfig.SPONSOR));
    }

    @Test
    public void resetRestoresEnabledDefaults() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        SponsorBlockCategoryRepository.setAllEnabled(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey, true);

        SponsorBlockCategoryRepository.resetDefaults(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey);

        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            assertEquals(category.defaultEnabled,
                    SponsorBlockCategoryRepository.isEnabled(preferences, category,
                            SponsorBlockCategoryRepositoryMigrationTest::enabledKey));
        }
    }

    @Test
    public void resetRemovesColorOverrides() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        SponsorBlockCategoryRepository.setColor(preferences, SponsorBlockCategoryConfig.SPONSOR,
                0xFF123456);

        SponsorBlockCategoryRepository.resetDefaults(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey);

        assertFalse(preferences.contains(SponsorBlockCategoryConfig.SPONSOR.colorKey()));
        assertEquals(0xFFABCDEF, SponsorBlockCategoryRepository.getColor(preferences,
                SponsorBlockCategoryConfig.SPONSOR, 0xFFABCDEF));
    }

    @Test
    public void resetRestoresDefaultBehavior() {
        final InMemoryPreferences preferences = new InMemoryPreferences();
        SponsorBlockCategoryRepository.setBehavior(preferences, SponsorBlockCategoryConfig.SPONSOR,
                SponsorBlockBehavior.MANUAL);

        SponsorBlockCategoryRepository.resetDefaults(preferences,
                SponsorBlockCategoryRepositoryMigrationTest::enabledKey);

        assertEquals(SponsorBlockBehavior.SKIP,
                SponsorBlockCategoryRepository.getBehavior(preferences,
                        SponsorBlockCategoryConfig.SPONSOR));
    }

    @Test
    public void markerOnlyHighlightRefusesPersistedSkipOrManualBehavior() {
        final InMemoryPreferences preferences = new InMemoryPreferences();

        SponsorBlockCategoryRepository.setBehavior(preferences,
                SponsorBlockCategoryConfig.HIGHLIGHT, SponsorBlockBehavior.SKIP);
        assertFalse(preferences.contains(SponsorBlockCategoryConfig.HIGHLIGHT.behaviorKey()));
        assertEquals(SponsorBlockBehavior.DONT_SKIP,
                SponsorBlockCategoryRepository.getBehavior(preferences,
                        SponsorBlockCategoryConfig.HIGHLIGHT));

        SponsorBlockCategoryRepository.setBehavior(preferences,
                SponsorBlockCategoryConfig.HIGHLIGHT, SponsorBlockBehavior.MANUAL);
        assertFalse(preferences.contains(SponsorBlockCategoryConfig.HIGHLIGHT.behaviorKey()));
        assertEquals(SponsorBlockBehavior.DONT_SKIP,
                SponsorBlockCategoryRepository.getBehavior(preferences,
                        SponsorBlockCategoryConfig.HIGHLIGHT));
    }

    private static String enabledKey(final SponsorBlockCategoryConfig category) {
        return category.id + "_enabled";
    }

    private static final class InMemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Nullable
        @Override
        public String getString(final String key, @Nullable final String defValue) {
            final Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Nullable
        @Override
        public Set<String> getStringSet(final String key,
                                        @Nullable final Set<String> defValues) {
            final Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defValues;
        }

        @Override
        public int getInt(final String key, final int defValue) {
            final Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(final String key, final long defValue) {
            final Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(final String key, final float defValue) {
            final Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public boolean getBoolean(final String key, final boolean defValue) {
            final Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(final String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new InMemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                final OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                final OnSharedPreferenceChangeListener listener) {
        }

        private final class InMemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(final String key, @Nullable final String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(final String key, @Nullable final Set<String> stringValues) {
                pending.put(key, stringValues == null ? null : new HashSet<>(stringValues));
                return this;
            }

            @Override
            public Editor putInt(final String key, final int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(final String key, final long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(final String key, final float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(final String key, final boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(final String key) {
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                for (final String key : removals) {
                    values.remove(key);
                }
                values.putAll(pending);
            }
        }
    }
}

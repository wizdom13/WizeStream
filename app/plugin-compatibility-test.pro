# Test-only process-state cleanup for actual external DEX installation/restore tests.
# The production release retains the public SDK, but not these private test targets.
-keep class org.schabi.newpipe.plugin.PluginManager { *; }

# The runner shares this app dependency, so AGP leaves it out of the test APK.
# Preserve its entry points for AndroidJUnitRunner in the minified test host.
-keep class androidx.tracing.Trace { *; }

# The selected fixture calls these host entry points from its separate test APK.
# Retain their signatures while still optimizing their implementations.
-keep,allowoptimization class org.schabi.newpipe.plugin.AndroidPluginPreferences {
    public <init>(android.content.Context, java.lang.String, java.util.List, java.lang.String);
    public android.content.SharedPreferences getSharedPreferences();
    public java.lang.String getString(java.lang.String);
    public static java.lang.String fileName(java.lang.String, java.lang.String);
    public static final org.schabi.newpipe.plugin.AndroidPluginPreferences$Companion Companion;
}

-keep,allowoptimization class org.schabi.newpipe.plugin.AndroidPluginPreferences$Companion {
    public java.lang.String fileName(java.lang.String, java.lang.String);
}

-keep,allowoptimization class org.schabi.newpipe.profiles.ProfileManager {
    public static final org.schabi.newpipe.profiles.ProfileManager INSTANCE;
    public static java.lang.String getActiveProfileId(android.content.Context);
    public static org.schabi.newpipe.profiles.ProfileRecord createProfile(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
    public static org.schabi.newpipe.profiles.ProfileRecord createProfile$default(...);
}

-keep,allowoptimization class org.schabi.newpipe.profiles.ProfileRecord {
    public java.lang.String getId();
}

-keep,allowoptimization class org.schabi.newpipe.plugin.PluginManager$InstalledPlugin {
    public java.lang.String getFileName();
}

-keep,allowoptimization class org.schabi.newpipe.plugin.PluginRuntimeLoader {
    public static java.lang.ClassLoader parentForPlugins(android.content.Context);
    public static boolean ownsClassName(java.lang.String);
}

-keep,allowoptimization class org.schabi.newpipe.util.PluginObjectInputStream {
    public <init>(java.io.InputStream);
}

-keep,allowoptimization class org.schabi.newpipe.util.PluginStateClassLoader {
    public static java.lang.ClassLoader withPlugins(java.lang.ClassLoader);
}

-keep,allowoptimization class org.schabi.newpipe.util.SerializedCache {
    public static org.schabi.newpipe.util.SerializedCache getInstance();
    public java.lang.String put(java.io.Serializable, java.lang.Class);
    public java.lang.Object take(java.lang.String, java.lang.Class);
}

-keep,allowoptimization class androidx.preference.PreferenceManager {
    public static android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context);
}

# The packaged-plugin feed regression uses the same isolated Room fixture as the
# app tests, then calls the real feed writer and What's New query from its test APK.
# These are test entry points, not additions to the external plugin SDK.
-keep,allowoptimization class org.schabi.newpipe.NewPipeDatabase {
    public static final org.schabi.newpipe.NewPipeDatabase INSTANCE;
    private static org.schabi.newpipe.database.AppDatabase databaseInstance;
    public static org.schabi.newpipe.database.AppDatabase getInstance(android.content.Context);
}

-keep,allowoptimization class androidx.room.Room {
    public static androidx.room.RoomDatabase$Builder inMemoryDatabaseBuilder(android.content.Context, java.lang.Class);
}
-keep,allowoptimization class androidx.room.RoomDatabase$Builder {
    public androidx.room.RoomDatabase$Builder allowMainThreadQueries();
    public androidx.room.RoomDatabase$Builder setQueryExecutor(java.util.concurrent.Executor);
    public androidx.room.RoomDatabase$Builder setTransactionExecutor(java.util.concurrent.Executor);
    public androidx.room.RoomDatabase build();
}
-keep,allowoptimization class androidx.room.RoomDatabase {
    public void close();
}

-keep,allowoptimization class org.schabi.newpipe.database.AppDatabase {
    public <init>();
    public org.schabi.newpipe.database.subscription.SubscriptionDAO subscriptionDAO();
    public org.schabi.newpipe.database.feed.dao.FeedDAO feedDAO();
}

-keep,allowoptimization interface org.schabi.newpipe.database.BasicDAO {
    public long insert(java.lang.Object);
}
-keep,allowoptimization class org.schabi.newpipe.database.subscription.SubscriptionDAO
-keep,allowoptimization class org.schabi.newpipe.database.feed.dao.FeedDAO {
    public java.util.List getMemberships(long);
}

-keep,allowoptimization class org.schabi.newpipe.database.subscription.SubscriptionEntity {
    public static final org.schabi.newpipe.database.subscription.SubscriptionEntity$Companion Companion;
    public void setProfileId(java.lang.String);
}
-keep,allowoptimization class org.schabi.newpipe.database.subscription.SubscriptionEntity$Companion {
    public org.schabi.newpipe.database.subscription.SubscriptionEntity from(org.schabi.newpipe.extractor.channel.ChannelInfo);
}

-keep,allowoptimization class org.schabi.newpipe.database.feed.model.FeedEntity {
    public long getStreamId();
    public long getFirstDiscoveredAt();
}
-keep,allowoptimization class org.schabi.newpipe.database.stream.StreamWithState {
    public org.schabi.newpipe.database.stream.model.StreamEntity getStream();
}
-keep,allowoptimization class org.schabi.newpipe.database.stream.model.StreamEntity {
    public java.lang.String getUrl();
    public int getServiceId();
    public java.lang.String getUploaderUrl();
    public java.time.OffsetDateTime getUploadDate();
}

-keep,allowoptimization class org.schabi.newpipe.local.feed.FeedScope {
    public <init>(int, int);
}
-keep,allowoptimization class org.schabi.newpipe.local.feed.FeedDatabaseManager {
    public <init>(android.content.Context, java.lang.String);
    public org.schabi.newpipe.database.AppDatabase database();
    public *** upsertAll(...);
    public static *** upsertAll$default(...);
    public *** getStreams(...);
    public static *** getStreams$default(...);
    public *** removeOrphansOrOlderStreams(...);
    public static *** removeOrphansOrOlderStreams$default(...);
}

# The separate test APK waits for the real feed query through this RxJava method.
# Its production callers can otherwise be optimized away from the minified host.
-keep,allowoptimization class io.reactivex.rxjava3.core.Maybe {
    public java.lang.Object blockingGet();
}

# Akachan Noise release rules.
#
# Media3, Compose and Glance all ship their own consumer rules, so nothing is
# needed for them here. The rules below cover the two places where code is
# looked up by name at runtime, which R8 would otherwise rename away.

# Room creates its database implementation by name. WorkManager (which Glance
# uses for widget updates) builds its database this way, so without this the
# app crashes on startup in release builds.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization finds generated serializers through the companion
# object; mix share codes and backup files depend on this.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.kurohi.akachannoise.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.kurohi.akachannoise.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.kurohi.akachannoise.**$$serializer { *; }

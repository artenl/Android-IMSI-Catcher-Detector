# Keep Room generated code and model classes used via reflection.
-keep class org.cellularprivacy.detector.data.** { *; }
-keep class org.cellularprivacy.detector.model.** { *; }

# osmdroid loads some classes/resources reflectively; keep it intact under R8.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

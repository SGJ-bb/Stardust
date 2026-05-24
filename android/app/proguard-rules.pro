-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.aicompanion.settings.SettingsManager { *; }
-keep class com.aicompanion.network.ApiClient { *; }
-keep class com.aicompanion.util.AppLogger { *; }
-keep class com.aicompanion.persona.PersonaManager { *; }
-keep class com.aicompanion.persona.Persona { *; }
-keep class com.aicompanion.migration.DataMigrationManager { *; }

-keep class androidx.security.crypto.** { *; }

-keepclassmembers class * {
    public <init>(...);
}

-keepclassmembers class com.aicompanion.models.** {
    *;
}

-keepclassmembers class com.aicompanion.persona.Persona$Companion {
    *;
}

-keepclassmembers class com.aicompanion.settings.ScheduledWake {
    *;
}

-keepclassmembers class com.aicompanion.groupchat.GroupChat {
    *;
}

-keepclassmembers class com.aicompanion.groupchat.GroupMessage {
    *;
}

-keepclassmembers class com.aicompanion.moments.Moment {
    *;
}

-keepclassmembers class com.aicompanion.moments.Comment {
    *;
}

-keepclassmembers class com.aicompanion.diary.DiaryEntry {
    *;
}

-keep class org.json.JSONObject
-keep class org.json.JSONArray

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

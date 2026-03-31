# Keep Room entities
-keep class com.example.molmoagent.data.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep accessibility service
-keep class com.example.molmoagent.accessibility.AgentAccessibilityService { *; }

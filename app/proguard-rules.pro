# Keep Vosk / JNA classes (native bindings rely on reflection).
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.**

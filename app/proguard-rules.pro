# Mempermudah membaca stack trace di build release.
-dontobfuscate

## NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keepclassmembers class org.schabi.newpipe.** {
    *** Companion;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## Rhino (dipakai NewPipeExtractor untuk mendekripsi signature/n-param YouTube)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**

## Protobuf (dipakai NewPipeExtractor)
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

## OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

## Paho MQTT (modul jaringan dimuat lewat ServiceLoader)
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

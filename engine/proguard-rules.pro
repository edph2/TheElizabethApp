# kotlinx.serialization: keep generated serializers for stored data classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class uk.elizabeth.voiceformat.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.elizabeth.voiceformat.**$$serializer { *; }

# sherpa-onnx: native code creates and reads these classes through JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Native code calls this callback's invoke(float[]) by name.
-keep class uk.elizabeth.speech.PiperVoice$AudioCallback { *; }

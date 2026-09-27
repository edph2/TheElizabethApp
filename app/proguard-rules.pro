# kotlinx.serialization: keep generated serializers for stored data classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class uk.elizabeth.aac.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.elizabeth.aac.core.**$$serializer { *; }

# sherpa-onnx: native code creates and reads these classes through JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# kotlinx.serialization: keep generated serializers for stored data classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class uk.elizabeth.aac.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.elizabeth.aac.core.**$$serializer { *; }

-keepclassmembers class uk.elizabeth.voiceformat.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.elizabeth.voiceformat.**$$serializer { *; }

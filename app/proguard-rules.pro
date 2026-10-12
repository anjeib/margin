-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ru.anjeib.margin.** { *** Companion; }
-keepclasseswithmembers class ru.anjeib.margin.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ru.anjeib.margin.**$$serializer { *; }
# The speech engine (sherpa-onnx) reads its config classes and fields by name from C++: nothing of it may be renamed or dropped.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# JavaMail (IMAP for forwarded letters) finds its providers and handlers by name.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.**
-dontwarn java.beans.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

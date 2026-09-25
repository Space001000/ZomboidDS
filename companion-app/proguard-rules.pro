# R8 rules for release builds. kotlinx.serialization, OkHttp, Coil and Compose ship their own rules;
# these cover what reflection or JSON in this app needs beyond that.

# Protocol DTOs are (de)serialized by kotlinx.serialization's generated serializers, which the
# library's own rules keep. Keep line numbers so crash reports point at real lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

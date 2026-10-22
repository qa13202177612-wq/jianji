# 保留行号信息，便于定位崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

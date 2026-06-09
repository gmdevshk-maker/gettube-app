# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================================
# youtubedl-android(yt-dlp 래퍼) 및 의존성 keep 규칙
# 이 AAR에는 consumer proguard 규칙이 포함돼 있지 않다(라이브러리 0.18.1 확인).
# Jackson이 리플렉션으로 모델 필드를 읽으므로 minify 시 keep 하지 않으면 release
# 빌드에서만 JSON 파싱이 깨진다(getInfo/업데이트 확인).
# ============================================================================
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# yt-dlp 출력(JSON)을 매핑하는 모델 — Jackson databind가 필드/생성자를 리플렉션으로 본다
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }

# Jackson(JSON 파서) — 리플렉션 기반
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**

# Apache Commons IO 등(라이브러리가 파일 처리에 사용)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**
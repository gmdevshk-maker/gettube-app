plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.app.gettube"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.app.gettube"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // youtubedl-android은 ABI별로 큰 네이티브 바이너리(yt-dlp/python + ffmpeg)를 포함한다.
    // 런타임이 풀어서 실행할 수 있도록 압축하지 않고 추출 가능하게 둔다.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
            )
        }
    }

    // 출력을 ABI별로 분리해 각 기기가 자기 ABI의 네이티브 바이너리(yt-dlp/python + ffmpeg)만
    // 받도록 한다. 약 210MB 통합 APK가 ABI별 약 50~60MB APK로 줄어든다.
    // arm64-v8a가 사실상 모든 최신 폰을 커버한다.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")   // arm64-v8a 단일 APK만 생성(app-arm64-v8a-debug.apk)
            isUniversalApk = false
        }
    }
}

// 각 ABI별 APK에 서로 다른 versionCode를 부여해 Play 스토어에서 공존할 수 있게 한다.
// 규칙: <abi 오프셋> * 1000 + 기본 versionCode(defaultConfig).
val abiVersionOffsets = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4,
)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
            val offset = abiVersionOffsets[abi]
            if (offset != null) {
                output.versionCode.set(offset * 1000 + 1)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // YouTube 다운로드 엔진(내부적으로 yt-dlp) + MP3 추출/병합용 ffmpeg
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
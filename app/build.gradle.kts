import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 서명 정보는 저장소에 커밋하지 않는 keystore.properties(루트)에서 읽는다.
// 파일이 없으면(예: CI에 아직 미설정) release 서명 설정을 건너뛴다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
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
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // keystore.properties가 있을 때만 release 서명 구성을 만든다.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // 서명 구성이 존재할 때만 연결한다(미설정 환경에서 빌드 실패 방지).
            signingConfig = signingConfigs.findByName("release")
            // R8 코드 축소 + 리소스 축소. material-icons-extended처럼 일부만 쓰는 대형
            // 의존성에서 미사용 코드/리소스를 제거한다. 단, youtubedl-android는 Jackson
            // 리플렉션을 쓰고 AAR에 consumer 규칙이 없어 proguard-rules.pro의 keep이 필수다.
            isMinifyEnabled = true
            isShrinkResources = true
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
            // APK 파일명을 "GetTube_<versionName>.apk" 형태로 출력한다(예: GetTube_1.0.apk).
            // versionName은 defaultConfig 값이며, debug/release는 서로 다른 폴더
            // (outputs/apk/<type>/)에 생성된다.
            val versionName = output.versionName.orNull ?: "unversioned"
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("GetTube_$versionName.apk")
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
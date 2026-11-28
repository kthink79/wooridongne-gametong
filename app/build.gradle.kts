plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.game.gametong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.game.gametong"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // 기존 SLG·TCG·기록장과 같은 고정 debug keystore(SHA256 1C:DB:4F:63…)로 서명해
    // 이후 업데이트가 이 데모 위에 그대로 설치되게 한다.
    signingConfigs {
        val repoKeystore = file("debug.keystore")
        val homeDir = System.getenv("HOME") ?: System.getProperty("user.home")
        val homeKeystore = file("$homeDir/.android/debug.keystore")
        val keystore = when {
            repoKeystore.exists() -> repoKeystore
            homeKeystore.exists() -> homeKeystore
            else -> null
        }
        if (keystore != null) {
            getByName("debug") {
                storeFile = keystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    // 단위 테스트는 한 프로세스에서 차례로 돌린다(기기·CI 메모리 절약, 결과 재현성).
    testOptions {
        unitTests.all {
            it.maxHeapSize = "1024m"
            it.maxParallelForks = 1
            it.jvmArgs("-XX:ActiveProcessorCount=1", "-XX:MaxMetaspaceSize=320m")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.molex.nextpair"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.molex.nextpair"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "3.3"
    }

    // Постоянный ключ: путь и пароль берутся из ~/.gradle/gradle.properties (или переменных окружения в CI).
    val keystore = (findProperty("NEXTPAIR_KEYSTORE") ?: System.getenv("NEXTPAIR_KEYSTORE"))?.toString()
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = (findProperty("NEXTPAIR_STORE_PASSWORD") ?: System.getenv("NEXTPAIR_STORE_PASSWORD")).toString()
            keyAlias = (findProperty("NEXTPAIR_KEY_ALIAS") ?: System.getenv("NEXTPAIR_KEY_ALIAS") ?: "nextpair").toString()
            keyPassword = (findProperty("NEXTPAIR_KEY_PASSWORD") ?: System.getenv("NEXTPAIR_KEY_PASSWORD")).toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Отладочная сборка ставится рядом с основной и не мешает ей.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // APK сразу с версией в имени: UniTime-3.3.apk
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "UniTime-${variant.versionName}.apk"
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

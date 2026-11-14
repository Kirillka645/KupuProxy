plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Never fall back to a runner-generated debug key. The existing developer key must
// be supplied explicitly to preserve update compatibility with installed APKs.
val releaseKeyNames = listOf("KUPU_KEYSTORE_PATH", "KUPU_KEYSTORE_PASSWORD", "KUPU_KEY_ALIAS", "KUPU_KEY_PASSWORD")
val releaseKeyValues = releaseKeyNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseKey = releaseKeyValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.kupuproxy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kupuproxy.app"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.gradleProperty("kupuVersionCode").get().toInt()
        versionName = providers.gradleProperty("kupuVersion").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO", "\"Kirillka645/KupuProxy\"")
        buildConfigField("boolean", "FEATURE_CHANNEL_FEED", "false")
        buildConfigField(
            "String",
            "SOURCES_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/Kirillka645/KupuProxy/main/sources_manifest.json\""
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(releaseKeyValues.getValue("KUPU_KEYSTORE_PATH")!!)
                storePassword = releaseKeyValues.getValue("KUPU_KEYSTORE_PASSWORD")
                keyAlias = releaseKeyValues.getValue("KUPU_KEY_ALIAS")
                keyPassword = releaseKeyValues.getValue("KUPU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    // Общая логика: парсер, проверка прокси, дизайн-токены. Используется и десктоп-клиентом.
    implementation(project(":shared"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// Validate only when release tasks run; debug builds and Desktop tests need no secrets.
val validateReleaseSigning by tasks.registering {
    doLast {
        val missing = releaseKeyNames.filter { releaseKeyValues[it].isNullOrBlank() }
        check(missing.isEmpty()) { "Release signing requires: ${missing.joinToString()}. See docs/releases/SIGNING.md" }
        check(file(releaseKeyValues.getValue("KUPU_KEYSTORE_PATH")!!).isFile) { "Release keystore does not exist" }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }

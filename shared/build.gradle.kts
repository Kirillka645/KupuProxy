plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        // Общий «jvm»-слой для androidMain и desktopMain: java.net / javax.crypto доступны на обеих
        // платформах, но недоступны в чистом commonMain.
        val jvmCommon by creating { dependsOn(commonMain.get()) }
        val jvmCommonTest by creating { dependsOn(commonTest.get()) }
        val desktopMain by getting { dependsOn(jvmCommon) }
        val desktopTest by getting { dependsOn(jvmCommonTest) }
        androidMain.get().dependsOn(jvmCommon)
        androidUnitTest.get().dependsOn(jvmCommonTest)

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }

        jvmCommon.dependencies {
            implementation("org.json:json:20240303")
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }

        jvmCommonTest.dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}

android {
    namespace = "com.kupuproxy.shared"
    compileSdk = 35

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
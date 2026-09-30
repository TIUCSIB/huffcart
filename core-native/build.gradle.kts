plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.huffcart.core.libretro"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        externalNativeBuild { cmake {} }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/c/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // LibretroCore 的公共 API 直接暴露 RetroCore 契约类型，须用 api 透传
    api(project(":core-bridge"))
}

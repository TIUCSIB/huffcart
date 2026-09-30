plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 纯 Kotlin/JVM 模块：零 Android 依赖，保证桥接逻辑可在桌面 JVM 直接调试（design.md 决策 2）
// 模块内无 Java 源码；Java/Kotlin 目标统一钉在 17，与宿主 JDK（21）解耦
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}

// JUnit4 的 URLClassLoader 在非 ASCII 项目路径（F:\自己写的项目\…）下无法加载测试类，
// 因此测试统一走 JUnit Platform（基于 URI 解析，不受路径字符集影响）
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

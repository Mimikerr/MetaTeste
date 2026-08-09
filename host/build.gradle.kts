plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.example.metateste.host.MainKt")
    // sun.jnu.encoding defaults to the Windows ANSI codepage (e.g. Cp1252) on this JVM, which
    // mangles the accented pt-BR text Vosk hands back over JNI unless forced to UTF-8.
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.vosk)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.junit)
}

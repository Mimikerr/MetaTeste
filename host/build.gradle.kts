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
        // Ktor's CIO client engine fails outbound HTTPS calls with "Cannot assign requested
        // address" on this Windows setup when it attempts IPv6 first; forcing IPv4 avoids it.
        "-Djava.net.preferIPv4Stack=true",
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.vosk)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

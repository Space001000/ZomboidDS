plugins {
    `java-library`
}

dependencies {
    api(libs.nanohttpd.websocket)
    implementation(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

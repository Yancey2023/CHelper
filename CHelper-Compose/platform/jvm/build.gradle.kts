plugins { id("chelper.jvm-library") }

dependencies {
    implementation(libs.crunch)
    api(libs.ktor.client.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.brotli)
    api(project(":shared:domain"))
    testImplementation(libs.junit)
}

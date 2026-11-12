plugins { id("chelper.jvm-library") }

dependencies {
    implementation(libs.crunch)
    api(project(":shared:domain"))
    testImplementation(libs.junit)
}

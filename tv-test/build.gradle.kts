plugins { alias(libs.plugins.android) }
android {
    namespace = "com.reiniertutoriales.vireotest"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "com.reiniertutoriales.vireotest"; minSdk = 24; targetSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

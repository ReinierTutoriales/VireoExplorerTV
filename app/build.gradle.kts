plugins { alias(libs.plugins.android); alias(libs.plugins.compose) }
android {
 namespace = "com.reiniertutoriales.vireoexplorertv"
 compileSdk { version = release(37) { minorApiLevel = 2 } }
 defaultConfig { applicationId = "com.reiniertutoriales.vireoexplorertv"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}
dependencies {
    implementation(libs.smbj) implementation(libs.media3.exoplayer); implementation(libs.media3.ui); implementation(libs.media3.session); implementation(libs.tv.material); implementation(libs.compose.foundation); implementation(libs.activity.compose); implementation(libs.coroutines); testImplementation("junit:junit:4.13.2") }

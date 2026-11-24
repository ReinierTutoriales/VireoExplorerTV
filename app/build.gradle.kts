plugins { alias(libs.plugins.android); alias(libs.plugins.compose) }
android {
 namespace = "com.reiniertutoriales.vireoexplorertv"
 compileSdk = 37
 defaultConfig { applicationId = "com.reiniertutoriales.vireoexplorertv"; minSdk = 24; targetSdk = 37; versionCode = 1; versionName = "0.1.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}
dependencies { implementation(libs.tv.material); implementation(libs.compose.foundation); implementation(libs.activity.compose); implementation(libs.coroutines); testImplementation("junit:junit:4.13.2") }

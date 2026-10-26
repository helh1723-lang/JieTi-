plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt")
 id("io.objectbox")
}
android {
 namespace = "cn.jieti"
 compileSdk = 35
 defaultConfig { applicationId = "cn.jieti"; minSdk = 31; targetSdk = 35; versionCode = 13; versionName = "0.2.3"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true
   isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
  }
  // Local optimized delivery: compatible with existing personal Debug installs.
  // Official release remains unsigned until a private release signing config is supplied.
  create("tablet") {
   initWith(getByName("release"))
   signingConfig = signingConfigs.getByName("debug")
   matchingFallbacks += listOf("release")
   isDebuggable = false
  }
 }
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
}

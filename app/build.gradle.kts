plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.tang.wechatassistant"; compileSdk = 35
    defaultConfig { applicationId = "com.tang.wechatassistant"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "1.3" }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { jvmToolchain(17) }

dependencies { implementation("androidx.core:core-ktx:1.13.1") }

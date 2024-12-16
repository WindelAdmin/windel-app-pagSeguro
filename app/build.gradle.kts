plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.devtools.ksp") version "1.8.10-1.0.9" apply true
}

val apiHostProd = "https://winpay-prod.windel.com.br"
val portProd = "3333"
val apiHostHml = "https://winpay-develop.windel.com.br"
val portHml = "3002"
val apiHostDev = "http://192.168.1.82"
val portDev = "3334"
val apiKey = "zOds60ZPbh4iHzMImrXafcDMvBi9RCMiJtOjTXiFbwtTFAoUBbEDrNCiKIbiqLUKlemc7Sa4OEMGvcfDu1BzGlqme4yfDR9yVbH1jfUqnysSabetplGY5DLAODtbHTmF"

android {
    namespace = "br.com.windel.pos"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "br.com.windel.pos"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            buildConfigField("String", "WINDEL_POS_HOST", "\"$apiHostProd\"")
            buildConfigField("String", "WINDEL_POS_API_KEY", "\"$apiKey\"")
            buildConfigField("String", "WINDEL_POS_AUTH_USER", "\"d2luZGVsdXNlcg==\"")
            buildConfigField("String", "WINDEL_POS_AUTH_PASS", "\"dzFuZDNsQEAyMzIw\"")
        }
        debug {
            buildConfigField("String", "WINDEL_POS_HOST", "\"$apiHostHml\"")
            buildConfigField("String", "WINDEL_POS_API_KEY", "\"$apiKey\"")
            buildConfigField("String", "WINDEL_POS_AUTH_USER", "\"d2luZGVsdXNlcg==\"")
            buildConfigField("String", "WINDEL_POS_AUTH_PASS", "\"dzFuZDNsQEAyMzIw\"")
        }
    }
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.plugpagwrapper)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    val roomVersion = "2.4.1"
    ksp("androidx.room:room-compiler:2.5.0")
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")
    implementation("androidx.room:room-rxjava3:$roomVersion")
    implementation("androidx.room:room-guava:$roomVersion")
    implementation("androidx.room:room-paging:2.4.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.11")
    implementation("com.airbnb.android:lottie:6.1.0")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

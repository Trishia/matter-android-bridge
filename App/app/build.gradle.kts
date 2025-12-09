import com.matter.buildsrc.Deps
import com.matter.buildsrc.Versions

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.matter.bridge.app"
    compileSdk = 34
    buildToolsVersion = Versions.buildToolsVersion

    defaultConfig {
        applicationId = "com.matter.bridge.app"
        minSdk = Versions.minSdkVersion
        targetSdk = Versions.targetSdkVersion
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            packagingOptions {
                jniLibs.keepDebugSymbols.add("**/*.so")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java", 
                "../../java/src",
                "../../../../../src/platform/android/java",
                "../../../../../src/app/server/java"
            )
            jniLibs.setSrcDirs(listOf("libs/jniLibs"))
        }
    }
    packagingOptions {
        jniLibs.pickFirsts.add("**/*.so")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(Deps.AndroidX.core)
    implementation(Deps.AndroidX.appcompat)
    implementation(Deps.AndroidX.constraintLayout)
    implementation(Deps.AndroidX.recyclerView)
    implementation(Deps.material)

    implementation(Deps.zxing)
    implementation(Deps.timber)

    testImplementation(Deps.Test.junit)
    androidTestImplementation(Deps.Test.junitExt)
    androidTestImplementation(Deps.Test.espresso)
    implementation("com.google.code.gson:gson:2.10.1")
}
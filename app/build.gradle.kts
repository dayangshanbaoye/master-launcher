plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.protobuf")
}

android {
    namespace = "com.rubyketang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rubyketang.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    // §2.5 展示区图片来源：SAF 选文件夹后用 DocumentFile 列目录，不申请 READ_MEDIA_IMAGES。
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

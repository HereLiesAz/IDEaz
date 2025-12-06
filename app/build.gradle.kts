plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val major = versionProps.getProperty("major", "1").toInt()
val minor = versionProps.getProperty("minor", "0").toInt()
val patch = versionProps.getProperty("patch", "3").toInt()
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.hereliesaz.ideaz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 30

        targetSdk = 36
        versionCode = major * 1000000 + minor * 10000 + patch * 100 + buildNumber
        versionName = "$major.$minor.$patch.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"
            )
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("mime.types")
            excludes.add("META-INF/THIRD-PARTY.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/plexus/components.xml")
            excludes.add("plugin.properties")
            pickFirsts.add("META-INF/sisu/javax.inject.Named")
            pickFirsts.add("**/*.so")
            pickFirsts.add("**/*.jnilib")
            pickFirsts.add("**/*.kotlin_builtins")
            pickFirsts.add("**/*.kotlin_module")
            pickFirsts.add("misc/registry.properties")
        }
    }
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "commons-logging" && requested.name == "commons-logging") {
                useTarget("org.slf4j:jcl-over-slf4j:1.7.30")
                because("Avoids duplicate classes with jcl-over-slf4j")
            }
        }
        force(libs.jna)
    }
}

androidComponents.onVariants { variant ->
    variant.outputs.forEach { output ->
        val version = output.versionName.get()
        // Workaround: VariantOutput interface does not expose outputFileName in this AGP version.
        // We cast to the internal implementation to maintain the renaming feature.
        if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
            output.outputFileName.set("IDEaz-$version-${variant.name}.apk")
        }
    }
}

dependencies {
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.jaxb.api)
    implementation(libs.javax.annotation.api)
    implementation(libs.validation.api)
    implementation(libs.glassfish.el)
    implementation(libs.slf4j.simple)
    implementation(libs.guava)
    implementation(libs.nb.javac.android)
    implementation(libs.r8)
    implementation(libs.sora.editor)
    implementation(libs.scala.compiler) {
        exclude(group = "org.jline")
    }
    implementation(libs.smali)
    implementation(libs.baksmali)
    implementation(libs.org.eclipse.jgit)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kxml2)
    implementation(libs.kotlinx.serialization.json)

    // Maven Dependency Resolver (Aether)
    implementation(libs.maven.resolver.api)
    implementation(libs.maven.resolver.spi)
    implementation(libs.maven.resolver.util)
    implementation(libs.maven.resolver.impl)
    implementation(libs.maven.resolver.connector.basic)
    implementation(libs.maven.resolver.transport.http)
    implementation(libs.maven.core)
    implementation(libs.aether.transport.file)
    implementation(libs.maven.resolver.provider)
    implementation(libs.wagon.http.lightweight)
    implementation(libs.generativeai)
    implementation(libs.google.genai)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.compose.unstyled.core)
    implementation(libs.aznavrail)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.haze)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // https://mvnrepository.com/artifact/org.apache.maven.resolver/maven-resolver-supplier
    implementation(libs.resolver.maven.resolver.supplier)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lazysodium.android)
    implementation(libs.hiddenapibypass)
}

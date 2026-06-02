import java.text.SimpleDateFormat
import java.util.Date

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.mux.android.distribution)
}

android {
  compileSdk = 36

  namespace = "com.mux.core_android"

  defaultConfig {
    minSdk = 16

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

muxDistribution {
  devVersion(versionFromCommitHash("dev-"))
  releaseVersion(versionFromTag())
  artifactIds(just("android"))
  groupIds(just("com.mux.stats.sdk.muxstats"))
  publicReleaseIf(releaseIfCmdFlag("publicRelease"))

  dokkaConfig {
    moduleName = "Mux Data SDK for Media3, Base"
    footer = "(c) " + SimpleDateFormat("yyyy").format(Date()) + " Mux, Inc. Have questions or need help?" +
            " Contact support@mux.com"
  }

  pom {
    description.set("Supporting library for Mux Data integrations")
    inceptionYear.set("2022")
    url.set("https://github.com/muxinc/stats-sdk-android")
    organization {
      name.set("Mux, Inc")
      url.set("https://www.mux.com")
    }
    developers {
      developer {
        email.set("support@mux.com")
        name.set("The player and sdks team @mux")
        organization.set("Mux, inc")
      }
    }
  }

  packageDocs(releaseIfCmdFlag("publicRelease").call())
  packageSources(true)
  publishIf { it.contains("release", ignoreCase = true) }
  artifactoryConfig {
    contextUrl = "https://muxinc.jfrog.io/artifactory/"
    releaseRepoKey = "default-maven-release-local"
    devRepoKey = "default-maven-local"
  }
}

dependencies {
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.annotation.jvm)

  // dynamic version/compileOnly so the player SDKs on top of this can update core independently
  //noinspection GradleDynamicVersion
  compileOnly(libs.mux.core)
  //noinspection GradleDynamicVersion
  testImplementation(libs.mux.core)

  testImplementation(libs.androidx.test.ext.junit.ktx)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)
}

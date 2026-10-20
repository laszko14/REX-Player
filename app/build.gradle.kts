import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.screenshot)
}

android {
  // Host-side Compose preview screenshots: separate from the signed APK pipeline.
  experimentalProperties["android.experimental.enableScreenshotTest"] = true
  namespace = "xyz.mpv.rex"
  compileSdk = 36

  // Existing lint issues are recorded once in app/lint-baseline.xml.
  // New lint issues must still fail CI (do not disable abortOnError).
  lint {
    baseline = file("lint-baseline.xml")
  }

  defaultConfig {
    applicationId = "xyz.mpv.rex"
    minSdk = 26
    targetSdk = 36
    // Signed MPV Alpha builds override these properties with increasing version numbers.
    // Local/ordinary CI builds retain the upstream values.
    versionCode = providers.gradleProperty("mpvAlphaVersionCode").orNull?.toInt() ?: 214
    versionName = providers.gradleProperty("mpvAlphaVersionName").orNull ?: "5.3.0"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
    // Enable update feature by default
    buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "true")
    buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "false")
    buildConfigField("boolean", "IS_PREVIEW", "false")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      isUniversalApk = false
    }
  }

  // Preserve local -PreleaseKey* signing; also support a private keystore in CI.
  // Never store the keystore or passwords in source control.
  val alphaKeyStore = providers.gradleProperty("releaseKeyStore").orNull
    ?: providers.environmentVariable("MPV_ALPHA_KEYSTORE_PATH").orNull
  signingConfigs {
    create("release") {
      if (alphaKeyStore != null) {
        storeFile = file(alphaKeyStore)
        storePassword = providers.gradleProperty("releaseKeyStorePassword").orNull
          ?: providers.environmentVariable("MPV_ALPHA_KEYSTORE_PASSWORD").orNull
        keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
          ?: providers.environmentVariable("MPV_ALPHA_KEY_ALIAS").orNull
        keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
          ?: providers.environmentVariable("MPV_ALPHA_KEY_PASSWORD").orNull
      }
    }
  }

  buildTypes {
    named("release") {
      if (alphaKeyStore != null) {
        signingConfig = signingConfigs.getByName("release")
      }
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      ndk {
        debugSymbolLevel = "none"
      }
    }

    create("preview") {
      initWith(getByName("release"))
      signingConfig = null
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
      buildConfigField("boolean", "IS_PREVIEW", "true")
    }

    named("debug") {
      // Keep xyz.mpv.rex.debug stable so subsequent signed builds update in place.
      applicationIdSuffix = ".debug"
      if (!providers.gradleProperty("mpvAlphaVersionName").isPresent) {
        versionNameSuffix = "-${getCommitCount()}"
      }
      // Only signed distribution builds opt into the durable keystore.
      if (alphaKeyStore != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }

    // Auto Audio PR #55: independent diagnostic installation, no overwrite of mpv α.
    // Unlike debug, this variant has a stable separate package ID and launcher name.
    create("audio") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".audio"
      versionNameSuffix = "-audio-v47"
      matchingFallbacks.add("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }

  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
    aidl = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

androidComponents {
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
  )

  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .find { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier

      output.versionCode.set(
        (output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0)
      )
    }
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeSourceInformation = true
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  // Official AndroidX Compose Preview Screenshot Testing (no emulator / APK release).
  screenshotTestImplementation(platform(libs.androidx.compose.bom))
  screenshotTestImplementation(libs.screenshot.validation.api)
  screenshotTestImplementation(libs.androidx.ui.tooling)

  implementation(libs.splashScreen)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation("com.google.android.material:material:1.13.0")
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.mediasession)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)

  implementation(libs.accompanist.permissions)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
  implementation(libs.mediainfo.lib)
  // ARM64-only AAR: contains a MATCHED libmpv.so/libplayer.so PCM tap pair.
  // v47 release is fixed to the tag; APK versioning remains separate.
  implementation("com.github.laszko14:mpvRex-libmpv:native-v47-arm64-r1@aar")
  // Real offline frame-based VAD; consumes original decoded PCM, never FFT metadata.
  // MIT-licensed Android wrapper around WebRTC VAD, arm64 JNI artifact.
  implementation("com.github.gkonovalov.android-vad:webrtc:2.0.10")
  // Diagnostic-only neural models. Both bundle their own offline model assets.
  // No runtime network access, no change to existing libmpv / Lua filter graph.
  implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
  implementation("com.github.gkonovalov.android-vad:yamnet:2.0.10")
  // Our YAMNet adapter directly imports TensorFlow Task Audio classes. The
  // yamnet module declares this as implementation(), not a public API.
  implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")
  implementation(libs.androidx.security.crypto)

  // Network protocol libraries
  implementation(libs.smbj)
  implementation(libs.commons.net)
  implementation(libs.sardine.android) {
    exclude(group = "xpp3", module = "xpp3")
  }
  implementation(libs.nanohttpd)
  implementation(libs.lazycolumnscrollbar)
  implementation(libs.reorderable)
  implementation(libs.compose.markdown)

  // Unit Testing
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}

/* ---------------- Git helpers ---------------- */

fun getCommitCount(): String =
  providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
  }.standardOutput.asText.get().trim().ifEmpty { "0" }

fun getCommitSha(): String =
  providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
  }.standardOutput.asText.get().trim().ifEmpty { "unknown" }

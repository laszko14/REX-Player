pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenLocal()
    mavenCentral()
    maven(url = "https://mpvrex.github.io/mpvRex-libmpv")
    // MPV Alpha v47: immutable ARM64-only libmpv AAR published as a GitHub
    // Release asset from the successful native master CI. No JitPack rebuild,
    // and no extra streaming connection at playback time.
    ivy {
      url = uri("https://github.com/laszko14/mpvRex-libmpv/releases/download")
      patternLayout {
        artifact("[revision]/[artifact]-[revision].[ext]")
      }
      metadataSources { artifact() }
      content { includeGroup("com.github.laszko14") }
    }

    maven(url = "https://www.jitpack.io") {
      content {
        // Only use JitPack for specific dependencies to avoid unnecessary checks
        includeGroup("com.github.sfsakhawat999")
        includeGroup("com.github.gkonovalov.android-vad")
        includeGroup("io.github.abdallahmehiz")
        includeGroup("com.github.abdallahmehiz")
        includeGroup("com.github.K1rakishou")
        includeGroup("com.github.marlboro-advance")
        includeGroup("com.github.thegrizzlylabs")
        includeGroup("com.github.nanihadesuka")
        includeGroup("com.github.jeziellago")
      }
    }
  }
}

rootProject.name = "mpvEx"
include(":app")

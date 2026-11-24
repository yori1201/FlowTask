pluginManagement {
  repositories {
    // Mirror Maven Central (opsional, bisa dihapus): membuat build tahan
    // terhadap pembatasan akses rate-limit repo.maven.apache.org.
    maven("https://repo.huaweicloud.com/repository/maven") {
      name = "HuaweiCloudMirror"
    }
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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven("https://repo.huaweicloud.com/repository/maven") {
      name = "HuaweiCloudMirror"
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "TaskFlow"

include(":app")

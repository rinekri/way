@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id("kotlin-kapt")
  alias(libs.plugins.way)
}

android {
  compileSdk = 33

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  implementation(project(":way"))
  api(project(":sample-compose:login:routing"))
  api(project(":sample-compose:main:routing"))
  api(project(":sample-compose:main-parallel:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}

package ru.kode.way.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class WayPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val provider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) {
      it.group = "way"
      it.source(
        "src/commonMain/way"
      )
      it.include("**/*.txt")
    }
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.let {
      it.sourceSets.getByName("commonMain").kotlin.srcDir(provider)
    }
  }
}

internal const val LIBRARY_PACKAGE = "ru.kode.way"
internal const val DEFAULT_SCHEMA_CLASS_NAME = "NavigationSchema"

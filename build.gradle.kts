/*
 * Copyright (C) 2022 The Authors of projectnessie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.EncodingConfiguration
import java.time.Duration

plugins {
  eclipse
  idea
  `java-library`
  signing
  `maven-publish`
  id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.6"
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

val antlrVersion = "4.10.1"

val releaseVersion = "${antlrVersion}${if (project.hasProperty("patchRelease")) ".${project.property("patchRelease")}" else ""}"

group = "org.projectnessie"
version = releaseVersion
description = "Relocated antlr-runtime"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.antlr:antlr4-runtime:$antlrVersion")
}

java {
  withJavadocJar()
  withSourcesJar()
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "${project.group}"
      artifactId = project.name
      version = "${project.version}"
      //from(components["java"])
      project.shadow.component(this)
      artifact(project.tasks.findByName("javadocJar"))
      artifact(project.tasks.findByName("sourcesJar"))
      pom {
        name.set(project.name)
        description.set(project.description)
        packaging = "jar"
        inceptionYear.set("2022")
        url.set("https://github.com/projectnessie/nessie-antlr-runtime")
        developers {
          file(rootProject.file("gradle/developers.csv"))
                  .readLines()
                  .map { line -> line.trim() }
                  .filter { line -> !line.isEmpty() && !line.startsWith("#") }
                  .forEach { line ->
                    val args = line.split(",")
                    if (args.size < 3) {
                      throw GradleException("gradle/developers.csv contains invalid line '${line}'")
                    }
                    developer {
                      id.set(args[0])
                      name.set(args[1])
                      url.set(args[2])
                    }
                  }
        }
        contributors {
          file(rootProject.file("gradle/contributors.csv"))
                  .readLines()
                  .map { line -> line.trim() }
                  .filter { line -> !line.isEmpty() && !line.startsWith("#") }
                  .forEach { line ->
                    val args = line.split(",")
                    if (args.size > 2) {
                      throw GradleException("gradle/contributors.csv contains invalid line '${line}'")
                    }
                    contributor {
                      name.set(args[1])
                      url.set(args[2])
                    }
                  }
        }
        organization {
          name.set("Project Nessie")
          url.set("https://projectnessie.org")
        }
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
        scm {
          connection.set("scm:git:https://github.com/projectnessie/nessie-antlr-runtime")
          developerConnection.set("scm:git:https://github.com/projectnessie/nessie-antlr-runtime")
          url.set("https://github.com/projectnessie/nessie-antlr-runtime/tree/main")
          tag.set("main")
        }
        issueManagement {
          system.set("Github")
          url.set("https://github.com/projectnessie/nessie-antlr-runtime/issues")
        }
      }
    }
  }
}

signing {
  if (project.hasProperty("release")) {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
  }
}

tasks.named<Jar>("jar") {
  archiveClassifier.set("raw")
}

tasks.named<ShadowJar>("shadowJar") {
  dependencies {
    include(dependency("org.antlr:antlr4-runtime"))
  }
  relocate("org.antlr.v4.runtime", "org.projectnessie.shaded.org.antlr.v4.runtime")
  archiveClassifier.set("")
}

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

val ideName = "nessie-antlr-runtime ${rootProject.version.toString().replace(Regex("^([0-9.]+).*"), "$1")}"

idea {
  module {
    name = ideName
    isDownloadSources = true
    inheritOutputDirs = true
  }

  project {
    withGroovyBuilder {
      "settings" {
        val encodings: EncodingConfiguration = getProperty("encodings") as EncodingConfiguration
        val delegateActions: ActionDelegationConfig =
                getProperty("delegateActions") as ActionDelegationConfig

        delegateActions.testRunner = ActionDelegationConfig.TestRunner.CHOOSE_PER_TEST

        encodings.encoding = "UTF-8"
        encodings.properties.encoding = "UTF-8"
      }
    }
  }
}
// There's no proper way to set the name of the IDEA project (when "just importing" or syncing the
// Gradle project)
val ideaDir = projectDir.resolve(".idea")

if (ideaDir.isDirectory) {
  ideaDir.resolve(".name").writeText(ideName)
}

eclipse { project { name = ideName } }

tasks.register("writeVersionFile") {
  file("./version.txt").writeText(releaseVersion)
}

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// OR in ~/.gradle/gradle.properties set
//    sonatypeUsername
//    sonatypePassword
// Call targets:
//    publishToSonatype
//    closeAndReleaseSonatypeStagingRepository
nexusPublishing {
  transitionCheckOptions {
    // default==60 (10 minutes), wait up to 60 minutes
    maxRetries.set(360)
    // default 10s
    delayBetween.set(Duration.ofSeconds(10))
  }
  repositories { sonatype() }
}

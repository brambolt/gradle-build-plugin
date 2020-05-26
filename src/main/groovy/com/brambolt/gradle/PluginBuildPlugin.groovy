package com.brambolt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class PluginBuildPlugin implements Plugin<Project> {

  List<String> pluginIds = [
    'java-gradle-plugin',
    'java-library',
    'groovy',
    'maven-publish',
    'com.gradle.plugin-publish',
    'com.jfrog.artifactory',
    'org.ajoberstar.grgit'
  ]

  List<String> requiredProperties = [
    'artifactId',
    'pluginClass',
    'pluginDisplayName',
    'pluginId',
    'pluginTags',
    'pluginWebsite',
    'projectVersion',
    'vcsUrl'
  ]

  @Override
  void apply(Project project) {
    configurePlugins(project)
    checkProjectProperties(project)
    configureDerivedProperties(project)
    logProperties(project)
  }

  void configurePlugins(Project project) {
    pluginIds.each { project.plugins.apply(it) }
  }

  void checkProjectProperties(project) {
    List<String> missing = []
    requiredProperties.each { String propertyName ->
      if (!isProjectPropertySet(project, propertyName))
        missing.add(propertyName)
    }
    if (!missing.isEmpty())
      throw new GradleException(
        "Missing project properties:\n  ${missing.join('\n  ')}")
  }

  void isProjectPropertySet(Project project, String propertyName) {
    project.hasProperty(propertyName) && !propertyName.isEmpty()
  }

  void configureDerivedProperties(Project project) {
    final String SNAPSHOT = 'SNAPSHOT'
    project.ext {
      buildNumber = project.hasProperty('buildNumber') ? project.buildNumber : SNAPSHOT
      buildDate = project.hasProperty('buildDate') ? project.buildDate : new Date()
      vcsBranch = project.grgit.branch.current().fullName
      vcsCommit = project.grgit.head().abbreviatedId
    }
    project.version = ((SNAPSHOT != project.buildNumber)
      ? "${project.projectVersion}-${project.buildNumber}"
      : SNAPSHOT)
  }

  void logProperties(Project project) {
    project.logger.quiet("""
  Artifact id:          ${project.artifactId}
  Branch:               ${project.vcsBranch}
  Commit:               ${project.vcsCommit}
  Description:          ${project.description}
  Group:                ${project.group}
  Name:                 ${project.name}
  Plugin class:         ${project.pluginClass}
  Plugin display name:  ${project.pluginDisplayName}
  Plugin id:            ${project.pluginId}
  Plugin tags:          ${project.pluginTags}
  Plugin website:       ${project.pluginWebsite}
  VCS URL:              ${project.vcsUrl}
  Version:              ${project.version}""")
  }

  void configureRepositories(Project project) {
    project.repositories {
      maven {
        name = 'Plugin Portal'
        url = 'https://plugins.gradle.org/m2/'
      }
      mavenLocal()
      mavenCentral()
      jcenter()
    }
  }

  void configureDependencies(Project project) {
    project.dependencies {
      implementation(project.gradleApi())
      implementation(project.localGroovy())
      implementation('com.gradle.publish:plugin-publish-plugin:latest.release')
      implementation('com.jfrog.artifactory:com.jfrog.artifactory.gradle.plugin:latest.release')
      testImplementation('org.slf4j:slf4j-api:latest.release')
      testImplementation('junit:junit:latest.release')
      testImplementation('org.spockframework:spock-core:1.3-groovy-2.5')
      testRuntime('org.slf4j:slf4j-simple:latest.release')
    }
  }

  void configureJavaPlugin(Project project) {
    project.sourceCompatibility = 14
    project.targetCompatibility = 8
    project.sourceSets {
      compatTest {
        compileClasspath += project.main.output
        runtimeClasspath += project.main.output
      }
    }
    project.compileJava.options.encoding = 'UTF-8'
    project.compileTestJava.options.encoding = 'UTF-8'
  }

  void configureJarTask(Project project) {
    project.jar {
      baseName = project.artifactId
      manifest {
        attributes([
          'Build-Date'     : project.buildDate,
          'Build-Number'   : project.buildNumber,
          'Build-Version'  : project.version,
          'Git-Branch'     : project.vcsBranch,
          'Git-Commit'     : project.vcsCommit,
          'Product-Version': project.version
        ], 'Brambolt')
      }
    }
  }

  void configurePublishing(Project project) {
    project.publishing {
      publications {
        mavenJava(MavenPublication) {
          groupId = project.group
          artifactId = project.artifactId
          version = project.version
          from project.components.java
        }
      }
    }
  }

/*
publishing {
  repositories {
    maven {
      name = 'bintray'
      url = 'https://api.bintray.com/maven/ajoberstar/maven/gradle-defaults/;publish=1'
      credentials {
        username = System.env['BINTRAY_USER']
        password = System.env['BINTRAY_KEY']
      }
    }
  }
}
*/
  void configureArtifactory(Project project) {
    project.artifactory {
      contextUrl = project.mavenContextUrl
      publish {
        repository {
          repoKey = project.mavenRepoKey
          username = project.mavenUser
          password = project.mavenToken
          maven = true
        }
        defaults {
          publications('mavenJava')
        }
      }
      resolve {
        repository {
          repoKey = project.mavenRepoKey
          username = project.mavenUser
          password = project.mavenToken
          maven = true
        }
      }
    }
  }

  void configureGradlePlugin(Project project) {
    project.gradlePlugin {
      plugins {
        patchingPlugin {
          id = project.pluginId
          implementationClass = project.pluginClass
        }
      }
    }
  }

  void configurePluginBundle(Project project) {
    project.pluginBundle {
      website = project.pluginWebsite
      vcsUrl = project.vcsUrl
      description = project.description
      tags = project.pluginTags
      plugins {
        patchingPlugin {
          displayName = project.pluginDisplayName
        }
      }
    }
  }
}

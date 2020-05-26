package com.brambolt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

/**
 * Configures a Gradle build to build and publish a Gradle plugin.
 */
class PluginBuildPlugin implements Plugin<Project> {

  /**
   * The plugins to apply to the plugin build project.
   */
  List<String> pluginIds = [
    'java-gradle-plugin',
    'java-library',
    'groovy',
    'maven-publish',
    'com.gradle.plugin-publish',
    'com.jfrog.artifactory',
    'org.ajoberstar.grgit'
  ]

  /**
   * The project-specific properties that must be set.
   */
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

  /**
   * Applies the plugin and configures the build.
   * @param project The project to configure
   */
  @Override
  void apply(Project project) {
    configurePlugins(project)
    checkProjectProperties(project)
    configureDerivedProperties(project)
    logProperties(project)
    configureRepositories(project)
    configureDependencies(project)
    configureJavaPlugin(project)
    configureJarTask(project)
    configureJavadocJarTask(project)
    configureSourceJarTask(project)
    configurePublishing(project)
    configureArtifactory(project)
    configureGradlePlugin(project)
    configurePluginBundle(project)
  }

  /**
   * Applies the plugins in the <code>pluginIds</code> list.
   * @param project The project to configure
   * @see #pluginIds
   */
  void configurePlugins(Project project) {
    pluginIds.each { project.plugins.apply(it) }
  }

  /**
   * Checks that values have been provided for the required project properties.
   * @param project The project to configure
   * @see #requiredProperties
   */
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

  /**
   * Checks that the parameter property has a non-empty value for the parameter
   * property name.
   * @param project The project to check
   * @param propertyName The property name to check
   */
  static boolean isProjectPropertySet(Project project, String propertyName) {
    project.hasProperty(propertyName) && !propertyName.isEmpty()
  }

  /**
   * Defines additional properties that are derived from the required properties.
   * @param project The project to configure
   */
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

  /**
   * Logs the required and derived project properties.
   * @param project The project to configure
   */
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

  /**
   * Adds repository definitions.
   * @param project The project to configure
   */
  void configureRepositories(Project project) {
    project.repositories {
      mavenLocal()
      maven {
        url = project.artifactoryContextUrl
        credentials {
          username = project.artifactoryUser
          password = project.artifactoryToken
        }
      }
      maven {
        name = 'Plugin Portal'
        url = 'https://plugins.gradle.org/m2/'
      }
      mavenCentral()
      jcenter()
    }
  }

  /**
   * Adds dependencies.
   * @param project The project to configure
   */
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

  /**
   * Configures the Java plugin including source and target compatibility.
   * @param project The project to configure
   */
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

  /**
   * Configures jar task including manifest attributes.
   * @param project The project to configure
   */
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

  /**
   * Configures the Javadoc jar task.
   * @param project The project to configure
   */
  void configureJavadocJarTask(Project project) {
    Jar jar = project.task([type: Jar], 'javadocJar') as Jar
    jar.configure { Task t ->
      t.dependsOn('javadoc')
      t.classifier = 'javadoc'
      t.from(project.property('javadoc'))
    }
  }

  /**
   * Configures the source jar task.
   * @param project The project to configure
   */
  void configureSourceJarTask(Project project) {
    Jar jar = project.task([type: Jar], 'sourceJar') as Jar
    jar.configure { Task t ->
      t.dependsOn('jar')
      t.classifier = 'sources'
      t.from(project.mainSourceSet.getOutput())
      t.from(project.mainSourceSet.getAllSource())
    }
  }

  /**
   * Configures publishing.
   * @param project The project to configure
   */
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

  /**
   * Configures Artifactory publishing. Disabled by default.
   *
   * Set <code>artifactoryContextUrl</code>, <code>artifactoryRepoKey</code>,
   * <code>artifactoryUser</code> and <code>artifactoryToken</code> to enable
   * Artifactory publishing.
   *
   * @param project The project to configure
   */
  void configureArtifactory(Project project) {
    if (project.hasProperty('artifactoryContextUrl'))
      project.artifactory {
        contextUrl = project.artifactoryContextUrl
        publish {
          repository {
            repoKey = project.artifactoryRepoKey
            username = project.artifactoryUser
            password = project.artifactoryToken
            maven = true
          }
          defaults {
            publications('mavenJava')
          }
        }
        resolve {
          repository {
            repoKey = project.artifactoryRepoKey
            username = project.artifactoryUser
            password = project.artifactoryToken
            maven = true
          }
        }
      }
  }

  /**
   * Configures Bintray publishing. Disabled by default.
   *
   * Set <code>bintrayContextUrl</code>, <code>bintrayUser</code> and
   * <code>bintrayKey</code> to enable Bintray publishing.
   *
   * @param project The project to configure
   */
  void configureBintray(Project project) {
    if (project.hasProperty('bintrayContextUrl'))
      project.publishing {
        repositories {
          maven {
            name = 'bintray'
            url = project.bintrayContextUrl
            credentials {
              username = project.bintrayUser
              password = project.bintrayKey
            }
          }
        }
      }
    }

  /**
   * Configures the Gradle plugin to build and publish.
   *
   * @param project The project to configure
   */
  void configureGradlePlugin(Project project) {
    project.gradlePlugin {
      plugins {
        thePlugin {
          id = project.pluginId
          implementationClass = project.pluginClass
        }
      }
    }
  }

  /**
   * Configures the plugin bundle being published.
   *
   * @param project The project to configure
   */
  void configurePluginBundle(Project project) {
    project.pluginBundle {
      website = project.pluginWebsite
      vcsUrl = project.vcsUrl
      description = project.description
      tags = project.pluginTags
      plugins {
        thePlugin {
          displayName = project.pluginDisplayName
        }
      }
    }
  }
}

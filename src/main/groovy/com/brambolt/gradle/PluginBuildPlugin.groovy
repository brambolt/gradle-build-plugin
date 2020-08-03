/*
 * Copyright 2017-2020 Brambolt ehf.
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

package com.brambolt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar

/**
 * Configures a Gradle build to build and publish a Gradle plugin.
 */
class PluginBuildPlugin implements Plugin<Project> {

  static final String SNAPSHOT = 'SNAPSHOT'

  static final String ARTIFACTORY_PLUGIN_ID = 'com.jfrog.artifactory'

  static final String BINTRAY_PLUGIN_ID = 'com.jfrog.bintray'

  static final String GRADLE_PLUGIN_PUBLISH_PLUGIN_ID = 'com.gradle.plugin-publish'

  static final String SHADOW_PLUGIN_ID = 'com.github.johnrengelman.shadow'

  /**
   * The plugins to apply to the plugin build project.
   */
  static final List<String> PLUGIN_IDS = [
    'java-gradle-plugin',
    'java-library',
    GRADLE_PLUGIN_PUBLISH_PLUGIN_ID,
    'groovy',
    'maven-publish',
    SHADOW_PLUGIN_ID,
    ARTIFACTORY_PLUGIN_ID,
    BINTRAY_PLUGIN_ID,
    'org.ajoberstar.grgit'
  ]

  /**
   * The project-specific properties that must be set.
   */
  static List<String> REQUIRED_PROPERTIES = [
    'artifactId',
    'developers',
    'inceptionYear',
    'licenses',
    'pluginClass',
    'pluginDisplayName',
    'pluginId',
    'pluginTags',
    'pluginWebsite',
    'release',
    'vcsUrl'
  ]

  /**
   * Maps plugin identifier to project property. If a plugin identifier
   * is included in this map then the plugin will only be applied if the
   * project property is present with a non-empty value that does not
   * evaluate to false.
   */
  Map<String, Closure<Boolean>> pluginInclusion = [:]

  /**
   * Applies the plugin and configures the build.
   * @param project The project to configure
   */
  @Override
  void apply(Project project) {
    project.logger.debug("Applying ${getClass().getCanonicalName()}.")
    configureDerivedPropertiesWithoutPlugins(project)
    checkProjectProperties(project, REQUIRED_PROPERTIES)
    logProperties(project)
    configurePluginInclusion(project, pluginInclusion)
    configurePlugins(project, PLUGIN_IDS, pluginInclusion)
    configureDerivedPropertiesWithPlugins(project)
    configureRepositories(project)
    configureDependencies(project)
    configureJavaPlugin(project)
    configureJarTask(project)
    configureJavadocJarTask(project)
    configureSourceJarTask(project)
    configureShadowJarTask(project)
    configureJavaPublishing(project)
    configureArtifactory(project)
    configureBintray(project)
    configureGradlePlugin(project)
    configurePluginBundle(project)
    configureDefaultTasks(project)
  }

  static void configurePluginInclusion(Project project, Map<String, Closure<Boolean>> pluginInclusion) {
    pluginInclusion[ARTIFACTORY_PLUGIN_ID] = { isArtifactoryPublishingEnabled(project) }
    pluginInclusion[BINTRAY_PLUGIN_ID] = { isBintrayPublishingEnabled(project) }
    pluginInclusion[GRADLE_PLUGIN_PUBLISH_PLUGIN_ID] = { isPluginPublishingEnabled(project) }
    pluginInclusion[SHADOW_PLUGIN_ID] = { isShadowJarEnabled(project) }
  }

  /**
   * Applies the plugins in the <code>PLUGIN_IDS</code> list.
   * @param project The project to configure
   * @param pluginInclusion Optional losures to check plugin inclusion
   * @see #PLUGIN_IDS
   */
  static void configurePlugins(
    Project project,
    List<String> pluginIds,
    Map<String, Closure<Boolean>> pluginInclusion) {
    pluginIds.each { String pluginId ->
      configurePlugin(project, pluginId, pluginInclusion)
    }
  }

  /**
   * Applies the plugin identified by the parameter plugin identifier. If the
   * plugin identifier is included as a key in the <code>pluginInclusion</code>
   * map then it will only be applied if the value is present as a property on
   * the project with a non-empty value.
   * @param project The project to apply the plugin to
   * @param id The identifier of the plugin to apply
   * @param pluginInclusion Optional losures to check plugin inclusion
   */
  static void configurePlugin(
    Project project,
    String id,
    Map<String, Closure<Boolean>> pluginInclusion) {
    if (shouldApplyPlugin(project, id, pluginInclusion)) {
      project.logger.debug("Applying plugin ${id}.")
      project.plugins.apply(id)
    }
  }

  /**
   * Uses the <code>pluginInclusion</code> map to determine whether the plugin
   * identified by the parameter identifier should be applied to the project.
   *
   * @param project The project being configured
   * @param pluginId The plugin identifier
   * @param pluginInclusion Optional closures to check plugin inclusion
   * @return True iff the plugin should be applied to the project
   */
  static boolean shouldApplyPlugin(
    Project project,
    String pluginId,
    Map<String, Closure<Boolean>> pluginInclusion) {
    // The inclusion map constrains what can be included.
    // If the identifier is not in the inclusion map, we can include:
    boolean result = !pluginInclusion.containsKey(pluginId) ||
      // But if it is in the map, then the configured check must succeed:
      pluginInclusion[pluginId].call(project)
    result
  }

  /**
   * Determines whether produced artifacts should be published to Artifactory.
   *
   * <p>As of version 4.16.1, the Artifactory plugin can only be applied to the
   * root project using the <code>plugins { .. }</code> Gradle syntax. When the
   * plugin needs to be applied to subprojects, it has to be included as a build
   * script class path dependency and then applied using the
   * <code>apply plugin:</code> syntax. This plugin only supports applying the
   * Artifactory when configuring a root project. Multi-project builds can
   * instead be explicitly configured with a <code>subprojects { .. }</code>
   * clause in the root project.</code>
   *
   * <p>See https://www.jfrog.com/confluence/display/JFROG/Gradle+Artifactory+Plugin.</p>
   */
  static boolean isArtifactoryPublishingEnabled(Project project) {
    if (!project.rootProject.equals(project))
      return false // See comments above
    [
      'artifactoryContextUrl',
      'artifactoryRepoKey',
      'artifactoryUser',
      'artifactoryToken'
    ].every { isProjectPropertySet(project, it as String) }
  }

  /**
   * Determines whether the produced artifacts should be published to Bintray.
   *
   * Bintray publishing is only applicable for open source projects that define
   * the <code>isOpenSource</code> property with the value <code>true</code>.
   *
   * Bintray publishing is enabled when the Bintray URL and credential project
   * properties are provided, and the project build number is not SNAPSHOT.
   * (Bintray does not provide snapshot publishing; attempts to publish
   * snapshots simply crash the build.)
   *
   * @param project The project being configured
   * @return True iff artifacts should be published to Bintray
   */
  static boolean isBintrayPublishingEnabled(Project project) {
    (SNAPSHOT != project.buildNumber) &&
      isProjectPropertySet(project, 'bintrayContextUrl') &&
      isProjectPropertySet(project, 'bintrayKey') &&
      isProjectPropertySet(project, 'bintrayUser') &&
      isProjectPropertySet(project, 'isOpenSource')
  }

  /**
   * Determines whether the produced artifacts should be published to the
   * Gradle plugin portal.
   *
   * Plugin publishing is only applicable for open source projects that define
   * the <code>isOpenSource</code> property with the value <code>true</code>.
   *
   * Plugin publishing is enabled when the Gradle plugin key and secret project
   * properties are provided, and the project build number is not SNAPSHOT.
   *
   * @param project The project being configured
   * @return True iff artifacts should be published to the plugin portal
   */
  static boolean isPluginPublishingEnabled(Project project) {
    (SNAPSHOT != project.buildNumber) &&
      isProjectPropertySet(project, 'gradle.publish.key') &&
      isProjectPropertySet(project, 'gradle.publish.secret') &&
      isProjectPropertySet(project, 'isOpenSource')
  }

  /**
   * Checks whether a shadow jar should be built. This is determined by the
   * presence of the <code>buildShadowJar</code> project property, with a
   * non-empty value.
   * @param project The project to check
   * @return True iff a shadow jar should be built, else false
   */
  static boolean isShadowJarEnabled(Project project) {
    isProjectPropertySet(project,
      'com.brambolt.gradle.pluginbuild.buildShadowJar')
  }

  /**
   * Checks that values have been provided for the required project properties.
   * @param project The project to configure
   * @see #REQUIRED_PROPERTIES
   */
  static void checkProjectProperties(Project project, List<String> requiredProperties) {
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
   * Checks that the parameter property has a non-empty string that does not
   * evaluate to <code>false</code>,or a non-empty collection value.
   *
   * @param project The project to check
   * @param propertyName The property name to check
   */
  static boolean isProjectPropertySet(Project project, String propertyName) {
    if (!project.hasProperty(propertyName))
      return false
    Object value = project[propertyName]
    switch (value) {
      case null:
        return false
      case { it instanceof String || it instanceof GString }:
        return !value.toString().isEmpty() &&
          !false.toString().equalsIgnoreCase(value.toString().trim())
      case { it instanceof Collection }:
        return !(value as Collection).isEmpty()
      default:
        return true // Not null, not empty string or empty collection - okay
    }
  }

  /**
   * Defines additional properties that are derived from the required properties.
   * Some of these are used when figuring out which plugins to apply.
   *
   * @param project The project to configure
   */
  static void configureDerivedPropertiesWithoutPlugins(Project project) {
    project.ext {
      buildNumber = (project.hasProperty('buildNumber')
        ? project.buildNumber : SNAPSHOT)
      buildDate = (project.hasProperty('buildDate')
        ? project.buildDate : new Date().format('yyyy-MM-dd HH:mm:ss'))
    }
    project.version = ((SNAPSHOT != project.buildNumber)
      ? "${project.release}-${project.buildNumber}"
      : SNAPSHOT)
  }

  /**
   * Configure the properties that we couldn't get values for without plugins.
   * @param project The project to configure
   */
  static void configureDerivedPropertiesWithPlugins(Project project) {
    project.ext {
      vcsBranch = project.grgit.branch.current().fullName
      vcsCommit = project.grgit.head().abbreviatedId
    }
  }

  /**
   * Logs the required and derived project properties.
   * @param project The project to configure
   */
  void logProperties(Project project) {
    project.logger.info("""
  Artifact id:          ${project.artifactId}
  Description:          ${project.description}
  Group:                ${project.group}
  Name:                 ${project.name}
  Plugin class:         ${project.pluginClass}
  Plugin display name:  ${project.pluginDisplayName}
  Plugin id:            ${project.pluginId}
  Plugin tags:          ${project.pluginTags}
  Plugin website:       ${project.pluginWebsite}
  VCS URL:              ${project.vcsUrl}
  Version:              ${project.version}
""")
  }

  /**
   * Adds repository definitions.
   * @param project The project to configure
   */
  static void configureRepositories(Project project) {
    project.repositories {
      mavenLocal()
      if (project.hasProperty('artifactoryContextUrl')) {
        maven {
          name = 'artifactory'
          url = "${project.artifactoryContextUrl}/${project.artifactoryRepoKey}"
          if (project.hasProperty('artifactoryToken'))
            credentials {
              username = project.artifactoryUser
              password = project.artifactoryToken
            }
        }
      }
      if (project.hasProperty('bintrayContextUrl')) {
        maven {
          name = 'bintray'
          url = "${project.bintrayContextUrl}/${project.bintrayRepoKey}"
          if (project.hasProperty('bintrayKey'))
            credentials {
              username = project.bintrayUser
              password = project.bintrayKey
            }
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
  static void configureDependencies(Project project) {
    project.dependencies {
      DependencyHandler handler = project.getDependencies()
      // If we are building a shadow jar then the Gradle API and local
      // Groovy dependencies need to be added to the shadow configuration:
      String maybeShadowOverride = (isShadowJarEnabled(project) ? 'shadow' : 'implementation')
      handler.add(maybeShadowOverride, handler.gradleApi())
      handler.add(maybeShadowOverride, handler.localGroovy())
      // Test dependencies are added explicitly via testkit, not here.
    }
  }

  /**
   * Configures the Java plugin including source and target compatibility.
   * @param project The project to configure
   */
  static void configureJavaPlugin(Project project) {
    project.sourceCompatibility = 8
    project.targetCompatibility = 8
    project.compileJava.options.encoding = 'UTF-8'
    project.compileTestJava.options.encoding = 'UTF-8'
  }

  /**
   * Configures jar task including manifest attributes.
   * @param project The project to configure
   */
  static void configureJarTask(Project project) {
    project.jar {
      dependsOn(['test'])
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
  static void configureJavadocJarTask(Project project) {
    Jar jar = project.task([type: Jar], 'javadocJar') as Jar
    jar.baseName = project.artifactId
    jar.dependsOn('javadoc')
    jar.classifier = 'javadoc'
    jar.from(project.property('javadoc'))
  }

  /**
   * Configures the source jar task.
   * @param project The project to configure
   */
  static void configureSourceJarTask(Project project) {
    SourceSet main = project.sourceSets.getByName('main')
    Jar jar = project.task([type: Jar], 'sourceJar') as Jar
    jar.baseName = project.artifactId
    jar.dependsOn('jar')
    jar.classifier = 'sources'
    jar.from(main.getAllSource())
  }

  /**
   * Configures the shadow jar task.
   * @param project The project being configured
   */
  static void configureShadowJarTask(Project project) {
    if (isShadowJarEnabled(project))
      project.shadowJar {
        dependsOn(project.jar)
        zip64 = true
      }
  }

  /**
   * Configures publishing.
   * @param project The project to configure
   */
  static void configureJavaPublishing(Project project) {
    Object pomMetaData = {
      licenses {
        project.licenses.each {
          license {
            name it.name
            url it.url
            distribution 'repo'
          }
        }
      }
      developers {
        project.developers.each {
          developer {
            id it.id
            name it.name
            email it.email
          }
        }
      }
      scm {
        url project.vcsUrl
      }
    }
    project.publishing {
      publications {
        mavenJava(MavenPublication) {
          artifactId = project.artifactId
          groupId = project.group
          version = project.version
          from project.components.java
          artifact(project.javadocJar)
          artifact(project.sourceJar)
          // As of com.github.johnrengelman.shadow 6.0.0 we no londer need to
          // explicitly add a shadow jar artifact here (and doing so causes a
          // conflict because the plugin automatically does it...).
          pom.withXml {
            def root = asNode()
            root.appendNode('description', project.description)
            root.appendNode('inceptionYear', project.inceptionYear)
            root.appendNode('name', project.name)
            root.appendNode('url', project.vcsUrl)
            root.children().last() + pomMetaData
          }
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
  static void configureArtifactory(Project project) {
    if (!isArtifactoryPublishingEnabled(project))
      return
    if (project.hasProperty('artifactoryContextUrl')) {
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
            publishArtifacts = true
            publishPom = true
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
  }

  /**
   * Configures Bintray publishing. Disabled by default.
   *
   * Set <code>bintrayContextUrl</code>, <code>bintrayUser</code> and
   * <code>bintrayKey</code> to enable Bintray publishing.
   *
   * @param project The project to configure
   */
  static void configureBintray(Project project) {
    if (!isBintrayPublishingEnabled(project))
      return // No Bintray...
    project.publishing {
      repositories {
        maven {
          name = 'bintray'
          url = "${project.bintrayContextUrl}/${project.bintrayRepoKey}"
          credentials {
            username = project.bintrayUser
            password = project.bintrayKey
          }
        }
      }
    }
    project.bintray {
      user = project.bintrayUser
      key = project.bintrayKey
      publications = ['mavenJava']
      pkg {
        repo = 'public'
        name = project.artifactId.toString()
        userOrg = project.bintrayUser
        licenses = project.licenses.collect { it.id }
        vcsUrl = project.vcsUrl
        version {
          desc = project.version.toString()
          name = project.version.toString()
          released = new Date()
        }
      }
      // Bintray is publish, so the project build script must include an
      // explicit statement like `isOpenSource = true` to enable publishing:
      publish = isBintrayPublishingEnabled(project)
    }
    project.bintrayUpload {
      // Disable Bintray upload if creating a snapshot version:
      onlyIf { isBintrayPublishingEnabled(project) }
    }
  }

  /**
   * Configures the Gradle plugin to build and publish.
   *
   * @param project The project to configure
   */
  static void configureGradlePlugin(Project project) {
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
   * Snapshot versions are not published.
   *
   * Projects without an <code>isOpenSource</code> property that evaluates to
   * <code>true</code> are not published.
   *
   * @param project The project to configure
   */
  static void configurePluginBundle(Project project) {
    if (!isPluginPublishingEnabled(project))
      return // No plugin publishing...
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
    project.tasks.getByName('publishPlugins').configure {
      // Don't publish snapshot versions on the open Internet:
      onlyIf { SNAPSHOT != project.version }
      // Don't publish plugins that don't include an explicit build property
      // declaring they're open source:
      onlyIf { project.hasProperty('isOpenSource') && project.isOpenSource }
    }
  }

  /**
   * Configures the <code>local</code> and <code>all</code> tasks and assigns
   * all as the project default.
   *
   * @param project The project to configure
   */
  static void configureDefaultTasks(Project project) {
    Task publishToMavenLocal = project.tasks.getByName('publishToMavenLocal')
    Task artifactoryPublish = project.tasks.findByName('artifactoryPublish')
    Task publishPlugins = project.tasks.findByName('publishPlugins')
    Task bintrayUpload = project.tasks.findByName('bintrayUpload')
    Task local = project.task('local')
    local.group = 'Brambolt'
    local.dependsOn(publishToMavenLocal)
    if (null != artifactoryPublish)
      artifactoryPublish.dependsOn(local)
    if (null != bintrayUpload)
      bintrayUpload.dependsOn(local)
    if (null != publishPlugins)
      publishPlugins.dependsOn(local)
    if (null != artifactoryPublish) {
      if (null != publishPlugins)
        publishPlugins.dependsOn(artifactoryPublish)
      if (null != bintrayUpload)
        bintrayUpload.dependsOn(artifactoryPublish)
    }
    Task all = project.task('all')
    all.group = 'Brambolt'
    all.dependsOn(local)
    if (null != artifactoryPublish)
      all.dependsOn(artifactoryPublish)
    if (null != bintrayUpload)
      all.dependsOn(bintrayUpload)
    if (null != publishPlugins)
      all.dependsOn(publishPlugins)
    project.setDefaultTasks([all.name])
  }
}

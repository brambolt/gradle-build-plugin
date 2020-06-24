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

  static final String SHADOW_PLUGIN_ID = 'com.github.johnrengelman.shadow'

  static final String SHADOW_PLUGIN_INCLUSION = 'com.brambolt.gradle.pluginbuild.buildShadowJar'

  /**
   * The plugins to apply to the plugin build project.
   */
  List<String> pluginIds = [
    'java-gradle-plugin',
    'java-library',
    'groovy',
    'maven-publish',
    SHADOW_PLUGIN_ID,
    'com.gradle.plugin-publish',
    'com.jfrog.artifactory',
    'com.jfrog.bintray',
    'org.ajoberstar.grgit'
  ]

  /**
   * Maps plugin identifier to project property. If a plugin identifier
   * is included in this map then the plugin will only be applied if the
   * project property is present with a non-empty value.
   */
  Map<String, String> pluginInclusion = [:]


  /**
   * The project-specific properties that must be set.
   */
  List<String> requiredProperties = [
    'artifactId',
    'developers',
    'inceptionYear',
    'licenses',
    'pluginClass',
    'pluginDisplayName',
    'pluginId',
    'pluginTags',
    'pluginWebsite',
    'projectVersion',
    'vcsUrl'
  ]

  PluginBuildPlugin() {
    pluginInclusion[SHADOW_PLUGIN_ID] = SHADOW_PLUGIN_INCLUSION
  }

  /**
   * Applies the plugin and configures the build.
   * @param project The project to configure
   */
  @Override
  void apply(Project project) {
    project.logger.debug("Applying ${getClass().getCanonicalName()}.")
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
    configureShadowJarTask(project)
    configurePublishing(project)
    configureArtifactory(project)
    configureBintray(project)
    configureGradlePlugin(project)
    configurePluginBundle(project)
    configureDefaultTasks(project)
  }

  /**
   * Applies the plugins in the <code>pluginIds</code> list.
   * @param project The project to configure
   * @see #pluginIds
   */
  void configurePlugins(Project project) {
    pluginIds.each { String pluginId -> configurePlugin(project, pluginId) }
  }

  /**
   * Applies the plugin identified by the parameter plugin identifier. If the
   * plugin identifier is included as a key in the <code>pluginInclusion</code>
   * map then it will only be applied if the value is present as a property on
   * the project with a non-empty value.
   * @param project The project to apply the plugin to
   * @param id The identifier of the plugin to apply
   */
  void configurePlugin(Project project, String id) {
    if (shouldApplyPlugin(project, id)) {
      project.logger.debug("Applying plugin ${id}.")
      project.plugins.apply(id)
    }
  }

  /**
   * Uses the <code>pluginInclusion</code> map to determine whether the
   * plugin identified by the parameter identifier should be applied to the
   * project. The map associates plugins with project properties; the plugins
   * should be applied if the map either does not contain the identifier (but
   * the plugins list has it) or the map points to a project property with a
   * non-empty value.
   * @param project The project being configured
   * @param pluginId The plugin identifier
   * @return True iff the plugin should be applied to the project
   */
  boolean shouldApplyPlugin(Project project, String pluginId) {
    // The inclusion map constrains what can be included.
    // If the identifier is not in the inclusion map, we can just include it:
    !pluginInclusion.containsKey(pluginId) ||
      // But if it is in the map, then the property named needs to be there:
      isProjectPropertySet(project, pluginInclusion[pluginId])
  }

  /**
   * Determines whether the produced artifacts should be published to Bintray.
   * Bintray publishing is enabled when the Bintray URL and credential project
   * properties are provided, and the project build number is not SNAPSHOT.
   * (Bintray does not provide snapshot publishing; attempts to publish
   * snapshots simply crash the build.)
   * @param project The project being configured
   * @return True iff artifacts should be published to Bintray
   */
  boolean isBintrayPublishingEnabled(Project project) {
    project.hasProperty('bintrayContextUrl') &&
      (SNAPSHOT != project.buildNumber)
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
   * Checks that the parameter property has a non-empty string or collection
   * value for the parameter property name.
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
        return !value.toString().isEmpty()
      case { it instanceof Collection }:
        return !(value as Collection).isEmpty()
      default:
        return true // Not null, not empty string or empty collection - okay
    }
  }

  /**
   * Checks whether a shadow jar should be built. This is determined by the
   * presence of the <code>buildShadowJar</code> project property, with a
   * non-empty value.
   * @param project The project to check
   * @return True iff a shadow jar should be built, else false
   */
  static boolean isShadowJarEnabled(Project project) {
    isProjectPropertySet(project, SHADOW_PLUGIN_INCLUSION)
  }

  /**
   * Defines additional properties that are derived from the required properties.
   * @param project The project to configure
   */
  void configureDerivedProperties(Project project) {
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
    project.logger.info("""
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
  Version:              ${project.version}
""")
  }

  /**
   * Adds repository definitions.
   * @param project The project to configure
   */
  void configureRepositories(Project project) {
    project.repositories {
      mavenLocal()
      if (project.hasProperty('artifactoryContextUrl')) {
        maven {
          url = project.artifactoryContextUrl
          if (project.hasProperty('artifactoryToken'))
            credentials {
              username = project.artifactoryUser
              password = project.artifactoryToken
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
  void configureDependencies(Project project) {
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
  void configureJavaPlugin(Project project) {
    project.sourceCompatibility = 8
    project.targetCompatibility = 8
    project.compileJava.options.encoding = 'UTF-8'
    project.compileTestJava.options.encoding = 'UTF-8'
  }

  /**
   * Configures jar task including manifest attributes.
   * @param project The project to configure
   */
  void configureJarTask(Project project) {
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
  void configureJavadocJarTask(Project project) {
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
  void configureSourceJarTask(Project project) {
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
  void configureShadowJarTask(Project project) {
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
  void configurePublishing(Project project) {
    def pomMetaData = {
      licenses {
        project.licenses.each { l ->
          license {
            name l.name
            url l.url
            distribution 'repo'
          }
        }
      }
      developers {
        project.developers.each { dev ->
          developer {
            id dev.id
            name dev.name
            email dev.email
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
  void configureArtifactory(Project project) {
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
      project.tasks.getByName('artifactoryPublish').dependsOn(
        project.tasks.getByName('publishToMavenLocal'))
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
    if (isBintrayPublishingEnabled(project))
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
          released  = new Date()
        }
      }
      publish = true
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
    project.tasks.getByName('publishPlugins').configure {
      onlyIf { SNAPSHOT != project.version }
    }
  }

  /**
   * Configures the <code>local</code> and <code>all</code> tasks and assigns
   * all as the project default.
   *
   * @param project The project to configure
   */
  void configureDefaultTasks(Project project) {
    Task local = project.task('local')
    local.dependsOn(['publishToMavenLocal'])
    // If we're publishing to Artifactory, then insert appropriate dependencies:
    if (project.hasProperty('artifactoryContextUrl')) {
      project.tasks.getByName('publishPlugins')
        .dependsOn(['local', 'artifactoryPublish'])
      // If we're also publishing to Bintray, add those dependencies as well:
      if (project.hasProperty('bintrayContextUrl'))
        project.tasks.getByName('bintrayUpload')
          .dependsOn(['local', 'artifactoryPublish'])
    }
    Task all = project.task('all')
    all.dependsOn(['publishPlugins'])
    if (project.hasProperty('bintrayContextUrl'))
      all.dependsOn(['bintrayUpload'])
    project.setDefaultTasks([all.name])
  }
}

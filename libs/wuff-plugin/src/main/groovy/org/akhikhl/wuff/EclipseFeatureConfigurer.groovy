/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.xml.MarkupBuilder
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.bundling.Jar
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class EclipseFeatureConfigurer {

  static boolean isFeatureProject(Project proj) {
    proj != null && proj.extensions.findByName('wuff') && proj.wuff.extensions.findByName('features')
  }

  protected static final Logger log = LoggerFactory.getLogger(EclipseFeatureConfigurer)

  protected final Project project
  protected timeStamp

  EclipseFeatureConfigurer(Project project) {
    this.project = project
  }

  void apply() {

    if(!project.extensions.findByName('wuff')) {
      def configurer = new Configurer(project)
      configurer.apply()
    }

    if(project.wuff.extensions.findByName('feature')) {
      log.warn 'Attempt to apply {} more than once on project {}', this.getClass().getName(), project
      return
    }

    project.wuff.extensions.create('feature', EclipseFeatureExtension)

    project.wuff.extensions.create('features', EclipseFeaturesExtension)
    project.wuff.features.featuresMap[''] = project.wuff.feature

    project.configurations {
      feature {
        transitive = false
      }
    }

    project.afterEvaluate {

      if(!project.tasks.findByName('clean'))
        project.task('clean') {
          group = 'wuff'
          description = "deletes directory ${project.buildDir}"
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('featurePrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares feature configuration files'
        dependsOn { getPluginJarTasks() }
        inputs.property 'featuresProperties', {
          getNonEmptyFeatures().collect { featureExt ->
            [ featureId: getFeatureId(featureExt),
              featureLabel: getFeatureLabel(featureExt),
              featureVersion: getFeatureVersion(featureExt),
              featureProviderName: featureExt.providerName,
              featureCopyright: featureExt.copyright,
              featureLicenseUrl: featureExt.licenseUrl,
              featureLicenseText: featureExt.licenseText ]
          }
        }
        inputs.files { getPluginFiles() }
        outputs.files {
          getNonEmptyFeatures().collect { getFeatureXmlFile(it) }
        }
        doLast {
          getNonEmptyFeatures().each { writeFeatureXml(it) }
        }
      }

      project.task('featureArchive') {
        group = 'wuff'
        description = 'archives eclipse feature(s)'
        dependsOn project.tasks.featurePrepareConfigFiles
        inputs.files {
          getNonEmptyFeatures().collect { getFeatureXmlFile(it) }
        }
        outputs.files {
          getNonEmptyFeatures().collect { getFeatureArchiveFile(it) }
        }
        doLast {
          getNonEmptyFeatures().each { featureExt ->
            ArchiveUtils.jar getFeatureArchiveFile(featureExt), {
              from getFeatureTempDir(featureExt), {
                add getFeatureXmlFile(featureExt)
              }
            }
          }
        }
      }

      if(!project.tasks.findByName('build'))
        project.task('build') {
          group = 'wuff'
          description = 'builds current project'
        }

      project.tasks.build.dependsOn project.tasks.featureArchive

    } // afterEvaluate
  }

  File getFeatureArchiveFile(EclipseFeatureExtension featureExt) {
    new File(getFeatureOutputDir(), getFeatureId(featureExt) + '_' + getFeatureVersion(featureExt) + '.jar')
  }

  Collection<File> getFeatureArchiveFiles() {
    getNonEmptyFeatures().collect { getFeatureArchiveFile(it) }
  }

  Configuration getFeatureConfiguration(EclipseFeatureExtension featureExt) {
    project.configurations[featureExt.configuration ?: 'feature']
  }

  String getFeatureId(EclipseFeatureExtension featureExt) {
    featureExt.id ?: project.name.replace('-', '.')
  }

  String getFeatureLabel(EclipseFeatureExtension featureExt) {
    featureExt.label ?: project.name
  }

  File getFeatureOutputDir() {
    new File(project.buildDir, 'feature-output')
  }

  Collection<EclipseFeatureExtension> getFeatures() {
    project.wuff.features.featuresMap.values()
  }

  File getFeatureTempDir(EclipseFeatureExtension featureExt) {
    new File(project.buildDir, 'feature-temp/' + getFeatureId(featureExt) + '_' + getFeatureVersion(featureExt))
  }

  String getFeatureVersion(EclipseFeatureExtension featureExt) {
    mavenVersionToEclipseVersion(featureExt.version ?: project.version)
  }

  File getFeatureXmlFile(EclipseFeatureExtension featureExt) {
    new File(getFeatureTempDir(featureExt), 'feature.xml')
  }

  Collection<EclipseFeatureExtension> getNonEmptyFeatures() {
    getFeatures().findAll { hasPluginFiles(it) }
  }

  Collection<File> getPluginFiles() {
    getFeatures().collectMany { getPluginFiles(it) }
  }

  Collection<File> getPluginFiles(EclipseFeatureExtension featureExt) {
    getFeatureConfiguration(featureExt).files
  }

  Collection<Task> getPluginJarTasks() {
    getFeatures().collectMany { getPluginJarTasks(it) }
  }

  Collection<Task> getPluginJarTasks(EclipseFeatureExtension featureExt) {
    getFeatureConfiguration(featureExt).dependencies.findResults { dep ->
      dep instanceof ProjectDependency ? dep.dependencyProject.tasks.findByName('jar') : null
    }
  }

  boolean hasPluginFiles() {
    getFeatures().any { hasPluginFiles(it) }
  }

  boolean hasPluginFiles(EclipseFeatureExtension featureExt) {
    !getFeatureConfiguration(featureExt).isEmpty()
  }

  String mavenVersionToEclipseVersion(String version) {
    String eclipseVersion = version ?: '1.0.0'
    if(eclipseVersion == 'unspecified')
      eclipseVersion = '1.0.0'
    if(eclipseVersion.endsWith('-SNAPSHOT')) {
      if(timeStamp == null)
        timeStamp = new Date().format('yyyyMMddHHmmss')
      eclipseVersion = eclipseVersion.replace('-SNAPSHOT', '.' + timeStamp)
    }
    eclipseVersion
  }

  void writeFeatureXml(EclipseFeatureExtension featureExt) {
    File file = getFeatureXmlFile(featureExt)
    file.parentFile.mkdirs()
    file.withWriter { writer ->
      def xml = new MarkupBuilder(writer)
      xml.doubleQuotes = true
      xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
      String featureLabel = getFeatureLabel(featureExt)
      Map featureAttrs = [ id: getFeatureId(featureExt), version: getFeatureVersion(featureExt), label: featureLabel ].findAll { it.value }
      xml.feature featureAttrs, {

        if(featureLabel)
          description featureLabel

        if(featureExt.copyright)
          copyright featureExt.copyright

        if(featureExt.licenseUrl || featureExt.licenseText)
          license(([url: featureExt.licenseUrl].findAll { it.value }), featureExt.licenseText)

        getFeatureConfiguration(featureExt).files.each { f ->
          def manifest = ManifestUtils.getManifest(project, f)
          if(ManifestUtils.isBundle(manifest)) {
            String bundleSymbolicName = manifest.mainAttributes?.getValue('Bundle-SymbolicName')
            bundleSymbolicName = bundleSymbolicName.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
            plugin id: bundleSymbolicName, 'download-size': '0', 'install-size': '0', version: manifest.mainAttributes?.getValue('Bundle-Version'), unpack: false
          }
          else
            log.error 'Could not add {} to feature {}, because it is not an OSGi bundle', f.name, getFeatureId(featureExt)
        }
      }
    }
  }
}

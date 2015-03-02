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
    proj != null && proj.extensions.findByName('wuff') && proj.wuff.ext.has('featureList')
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

    if(project.wuff.ext.has('featureList')) {
      log.warn 'Attempt to apply {} more than once on project {}', this.getClass().getName(), project
      return
    }

    project.wuff.ext.featureList = []
    project.wuff.ext.defaultFeatureList = null
    
    project.wuff.metaClass {
      feature = { Object... args ->
        String id
        Closure closure
        for(def arg in args)
          if(arg instanceof String)
            id = arg
          else if(arg instanceof Closure)
            closure = arg
        if(!id)
          id = EclipseFeature.getDefaultId(project)
        def f = project.wuff.ext.featureList.find { it.id == id }
        if(f == null) {
          f = new EclipseFeature(project, id)
          project.wuff.ext.featureList.add(f)
        }
        if(closure != null) {
          closure.delegate = f
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure()
        }
      }
    }

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
            [ featureId: featureExt.id,
              featureLabel: featureExt.label,
              featureVersion: featureExt.version,
              featureProviderName: featureExt.providerName,
              featureCopyright: featureExt.copyright,
              featureLicenseUrl: featureExt.licenseUrl,
              featureLicenseText: featureExt.licenseText ]
          }
        }
        inputs.files { getPluginFiles() }
        outputs.files {
          getNonEmptyFeatures().collect { it.getTempFeatureXmlFile() }
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
          getNonEmptyFeatures().collect { it.getTempFeatureXmlFile() }
        }
        outputs.files {
          getNonEmptyFeatures().collect { it.getArchiveFile() }
        }
        doLast {
          getNonEmptyFeatures().each { featureExt ->
            ArchiveUtils.jar featureExt.getArchiveFile(), {
              from featureExt.getTempDir(), {
                add featureExt.getTempFeatureXmlFile()
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

  Collection<File> getFeatureArchiveFiles() {
    getNonEmptyFeatures().collect { it.getArchiveFile() }
  }

  Collection<EclipseFeature> getFeatures() {
    def result = project.wuff.ext.featureList
    if(!result) {
      result = project.wuff.ext.defaultFeatureList
      if(!result)
        result = project.wuff.ext.defaultFeatureList = [ new EclipseFeature(project, null) ]
    }
    result
  }

  Collection<EclipseFeature> getNonEmptyFeatures() {
    getFeatures().findAll { it.hasPluginFiles() }
  }

  Collection<File> getPluginFiles() {
    getFeatures().collectMany { it.getPluginFiles() }
  }

  Collection<Task> getPluginJarTasks() {
    getFeatures().collectMany { it.getPluginJarTasks() }
  }

  boolean hasPluginFiles() {
    getFeatures().any { it.hasPluginFiles() }
  }

  String mavenVersionToEclipseVersion(String version) {
    if(version.endsWith('-SNAPSHOT')) {
      if(timeStamp == null)
        timeStamp = new Date().format('yyyyMMddHHmmss')
      version = version.replace('-SNAPSHOT', '.' + timeStamp)
    }
    version
  }

  void writeFeatureXml(EclipseFeature featureExt) {
    File file = featureExt.getTempFeatureXmlFile()
    file.parentFile.mkdirs()
    file.withWriter { writer ->
      def xml = new MarkupBuilder(writer)
      xml.doubleQuotes = true
      xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
      Map featureAttrs = [ id: featureExt.id, version: mavenVersionToEclipseVersion(featureExt.version), label: featureExt.label ].findAll { it.value }
      xml.feature featureAttrs, {

        if(featureExt.label)
          description featureExt.label

        if(featureExt.copyright)
          copyright featureExt.copyright

        if(featureExt.licenseUrl || featureExt.licenseText)
          license(([url: featureExt.licenseUrl].findAll { it.value }), featureExt.licenseText)

        featureExt.getPluginFiles().each { f ->
          def manifest = ManifestUtils.getManifest(project, f)
          if(ManifestUtils.isBundle(manifest)) {
            String bundleSymbolicName = manifest.mainAttributes?.getValue('Bundle-SymbolicName')
            bundleSymbolicName = bundleSymbolicName.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
            plugin id: bundleSymbolicName, 'download-size': '0', 'install-size': '0', version: manifest.mainAttributes?.getValue('Bundle-Version'), unpack: false
          }
          else
            log.error 'Could not add {} to feature {}, because it is not an OSGi bundle', f.name, featureExt.id
        }
      }
    }
  }
}

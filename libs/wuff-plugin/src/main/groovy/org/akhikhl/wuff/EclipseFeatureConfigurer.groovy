/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.xml.MarkupBuilder
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
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

  protected static final Logger log = LoggerFactory.getLogger(EclipseFeatureConfigurer)

  protected final Project project
  protected final timeStamp = new Date().format('yyyyMMddHHmmss')

  EclipseFeatureConfigurer(Project project) {
    this.project = project
  }

  void apply() {

    def configurer = new Configurer(project)
    configurer.apply()

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
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('featurePrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares feature configuration files'
        dependsOn {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).dependencies.findResults { dep ->
              dep instanceof ProjectDependency ? dep.dependencyProject.tasks.findByName('build') : null
            }
          }
        }
        inputs.property 'featuresProperties', {
          project.wuff.features.featuresMap.collectEntries { id, featureExt ->
            [id, [
              featureId: getFeatureId(featureExt),
              featureLabel: getFeatureLabel(featureExt),
              featureVersion: getFeatureVersion(featureExt),
              featureProviderName: featureExt.providerName,
              featureCopyright: featureExt.copyright,
              featureLicenseUrl: featureExt.licenseUrl,
              featureLicenseText: featureExt.licenseText
            ]]
          }
        }
        inputs.files {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files
          }
        }
        outputs.files {
          project.wuff.features.featuresMap.findResults { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
              getFeatureXmlFile(featureExt)
          }
        }
        doLast {
          project.wuff.features.featuresMap.each { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
              writeFeatureXml(featureExt)
          }
        }
      }

      project.task('featureArchive') {
        group = 'wuff'
        description = 'archives eclipse feature(s)'
        dependsOn project.tasks.featurePrepareConfigFiles
        inputs.files {
          project.wuff.features.featuresMap.findResults { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
              getFeatureXmlFile(featureExt)
          }
        }
        outputs.files {
          project.wuff.features.featuresMap.findResults { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
              getFeatureArchiveFile(featureExt)
          }
        }
        doLast {
          project.wuff.features.featuresMap.findResults { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
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

  protected File getFeatureArchiveFile(EclipseFeatureExtension featureExt) {
    new File(getFeatureOutputDir(), getFeatureArchiveFileName(featureExt))
  }

  protected String getFeatureArchiveFileName(EclipseFeatureExtension featureExt) {
    getFeatureId(featureExt) + '_' + getFeatureVersion(featureExt) + '.jar'
  }

  protected Configuration getFeatureConfiguration(EclipseFeatureExtension featureExt) {
    project.configurations[featureExt.configuration ?: 'feature']
  }

  protected String getFeatureId(EclipseFeatureExtension featureExt) {
    featureExt.id ?: project.name.replace('-', '.')
  }

  protected String getFeatureLabel(EclipseFeatureExtension featureExt) {
    featureExt.label ?: project.name
  }

  protected File getFeatureOutputDir() {
    new File(project.buildDir, 'output')
  }

  protected File getFeatureTempDir(EclipseFeatureExtension featureExt) {
    new File(project.buildDir, 'feature-temp/' + getFeatureId(featureExt) + '_' + getFeatureVersion(featureExt))
  }

  protected String getFeatureVersion(EclipseFeatureExtension featureExt) {
    mavenVersionToEclipseVersion(featureExt.version ?: project.version)
  }

  protected File getFeatureXmlFile(EclipseFeatureExtension featureExt) {
    new File(getFeatureTempDir(featureExt), 'feature.xml')
  }

  protected String mavenVersionToEclipseVersion(String version) {
    String eclipseVersion = version ?: '1.0.0'
    if(eclipseVersion == 'unspecified')
      eclipseVersion = '1.0.0'
    eclipseVersion = eclipseVersion.replace('-SNAPSHOT', '.' + timeStamp)
    eclipseVersion
  }

  protected void writeFeatureXml(EclipseFeatureExtension featureExt) {
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

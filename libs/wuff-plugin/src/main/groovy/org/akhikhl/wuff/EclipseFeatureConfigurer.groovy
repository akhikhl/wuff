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
import org.gradle.api.tasks.Copy
import org.gradle.process.ExecResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class EclipseFeatureConfigurer {

  protected static final Logger log = LoggerFactory.getLogger(EclipseFeatureConfigurer)

  protected static mavenVersionToEclipseVersion(String version) {
    def eclipseVersion = version ?: '1.0.0'
    if(eclipseVersion == 'unspecified')
      eclipseVersion = '1.0.0'
    eclipseVersion = eclipseVersion.replace('-SNAPSHOT', '.qualifier')
    eclipseVersion
  }

  protected final Project project

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

      // File featureXmlFile = new File(featuresDir, "${getFeatureId()}/feature.xml")
      // File buildPropertiesFile = new File(featuresDir, "${getFeatureId()}/build.properties")
      // String featureOutputFileName = getFeatureId() + '-' + getFeatureVersion() + '.zip'
      // File featureAssembleOutputFile = new File(featureOutputDir, 'build.' + getFeatureVersion() + '/' + featureOutputFileName)
      // File featureBuildOutputFile = new File(project.buildDir, featureOutputFileName)

      if(!project.tasks.findByName('clean'))
        project.task('clean') {
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('featureRemoveStalePlugins') {
        group = 'wuff'
        description = 'removes stale plugins from feature source'
        dependsOn {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).dependencies.findResults { dep ->
              dep instanceof ProjectDependency ? dep.dependencyProject.tasks.findByName('build') : null
            }
          }
        }
        inputs.files {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files
          }
        }
        outputs.upToDateWhen {
          Set fileNames = project.wuff.features.featuresMap.collectMany({ id, featureExt ->
            getFeatureConfiguration(featureExt).files.collect { it.name }
          }) as Set
          !pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find { !fileNames.contains(it.name) }
        }
        doLast {
          Set fileNames = project.wuff.features.featuresMap.collectMany({ id, featureExt ->
            getFeatureConfiguration(featureExt).files.collect { it.name }
          }) as Set
          pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).findAll { !fileNames.contains(it.name) }.each {
            it.delete()
          }
        }
      }

      project.task('featureCopyPlugins', type: Copy) {
        group = 'wuff'
        description = 'copies dependency plugins to feature source'
        dependsOn project.tasks.featureRemoveStalePlugins
        inputs.files {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files
          }
        }
        outputs.dir pluginsDir
        from {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files
          }
        }
        into pluginsDir
      }

      project.task('featurePrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares feature configuration files'
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
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files ? [ getFeatureXmlFile(featureExt), getFeatureBuildPropertiesFile(featureExt) ] : []
          }
        }
        doLast {
          project.wuff.features.featuresMap.each { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files) {
              writeFeatureXml(featureExt)
              writeFeatureBuildPropertiesFile(featureExt)
            }
          }
        }
      }

      project.task('featureAssemble') {
        group = 'wuff'
        description = 'assembles eclipse feature(s)'
        dependsOn project.tasks.featureCopyPlugins
        dependsOn project.tasks.featurePrepareConfigFiles
        inputs.dir { getPluginsDir() }
        inputs.files {
          project.wuff.features.featuresMap.collectMany { id, featureExt ->
            getFeatureConfiguration(featureExt).files ? [ getFeatureXmlFile(featureExt), getFeatureBuildPropertiesFile(featureExt) ] : []
          }
        }
        outputs.files {
          project.wuff.features.featuresMap.findResults { id, featureExt ->
            if(getFeatureConfiguration(featureExt).files)
              getFeatureAssembleOutputFile(featureExt)
          }
        }
        doLast {
          File baseLocation = project.effectiveUnpuzzle.eclipseUnpackDir
          def equinoxLauncherPlugin = new File(baseLocation, 'plugins').listFiles({ it.name.matches ~/^org\.eclipse\.equinox\.launcher_(.+)\.jar$/ } as FileFilter)
          if(!equinoxLauncherPlugin)
            throw new GradleException("Could not build feature: equinox launcher not found in ${new File(baseLocation, 'plugins')}")
          equinoxLauncherPlugin = equinoxLauncherPlugin[0]

          def pdeBuildPlugin = new File(baseLocation, 'plugins').listFiles({ it.name.matches ~/^org.eclipse.pde.build_(.+)$/ } as FileFilter)
          if(!pdeBuildPlugin)
            throw new GradleException("Could not build feature: PDE build plugin not found in ${new File(baseLocation, 'plugins')}")
          pdeBuildPlugin = pdeBuildPlugin[0]
          if(!pdeBuildPlugin.isDirectory())
            throw new GradleException("Could not build feature: ${pdeBuildPlugin} exists, but is not a directory")
          File buildXml = new File(pdeBuildPlugin, 'scripts/build.xml')
          if(!buildXml.exists())
            throw new GradleException("Could not build feature: file ${buildXml} does not exist")
          File buildConfigDir = new File(pdeBuildPlugin, 'templates/headless-build')
          if(!buildConfigDir.exists())
            throw new GradleException("Could not build feature: directory ${buildConfigDir} does not exist")
          if(!buildConfigDir.isDirectory())
            throw new GradleException("Could not build feature: directory ${buildConfigDir} exists, but is not a directory")

          project.wuff.features.featuresMap.each { id, featureExt ->

            def outFile = getFeatureAssembleOutputFile(featureExt)
            if(outFile.exists())
              outFile.delete()

            if(getFeatureConfiguration(featureExt).files) {
              ExecResult result = project.javaexec {
                main = 'main'
                jvmArgs '-jar', equinoxLauncherPlugin.absolutePath
                jvmArgs '-application', 'org.eclipse.ant.core.antRunner'
                jvmArgs '-buildfile', buildXml.absolutePath
                jvmArgs '-Dbuilder=' + buildConfigDir.absolutePath
                jvmArgs '-DbuildDirectory=' + getFeatureOutputDir().absolutePath
                jvmArgs '-DbaseLocation=' + baseLocation.absolutePath
                jvmArgs '-DtopLevelElementId=' + getFeatureId(featureExt)
                jvmArgs '-DbuildType=build'
                jvmArgs '-DbuildId=' + getFeatureId(featureExt) + '-' + getFeatureVersion(featureExt)
              }
              result.assertNormalExitValue()
            }
          }
        }
      }

      project.task('featureCopyOutput') {
        group = 'wuff'
        description = 'copies feature output to final location'
        dependsOn project.tasks.featureAssemble
        inputs.files {
          project.wuff.features.featuresMap.collect { id, featureExt ->
            getFeatureAssembleOutputFile(featureExt)
          }
        }
        outputs.file {
          project.wuff.features.featuresMap.collect { id, featureExt ->
            getFeatureBuildOutputFile(featureExt)
          }
        }
        doLast {
          project.wuff.features.featuresMap.each { id, featureExt ->
            FileUtils.copyFile getFeatureAssembleOutputFile(featureExt), getFeatureBuildOutputFile(featureExt)
          }
        }
      }

      if(!project.tasks.findByName('build'))
        project.task('build', type: Copy) {
          group = 'wuff'
          description = 'builds current project'
        }

      project.tasks.build.dependsOn project.tasks.featureCopyOutput

    } // afterEvaluate
  }

  protected File getFeatureAssembleOutputFile(EclipseFeatureExtension featureExt) {
    new File(getFeatureOutputDir(), 'build.' + getFeatureId(featureExt) + '-' + getFeatureVersion(featureExt) + '/' + getFeatureId(featureExt) + '-' + getFeatureId(featureExt) + '-' + getFeatureVersion(featureExt) + '.zip')
  }

  protected File getFeatureBuildPropertiesFile(EclipseFeatureExtension featureExt) {
    new File(getFeaturesDir(), "${getFeatureId(featureExt)}/build.properties")
  }

  protected File getFeatureBuildOutputFile(EclipseFeatureExtension featureExt) {
    new File(project.buildDir, getFeatureBuildOutputFileName(featureExt))
  }

  protected String getFeatureBuildOutputFileName(EclipseFeatureExtension featureExt) {
    getFeatureId(featureExt) + '-' + getFeatureVersion(featureExt) + '.zip'
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
    new File(project.buildDir, 'featureOutput')
  }

  protected File getFeaturesDir() {
    new File(getFeatureOutputDir(), 'features')
  }

  protected String getFeatureVersion(EclipseFeatureExtension featureExt) {
    mavenVersionToEclipseVersion(featureExt.version ?: project.version)
  }

  protected File getFeatureXmlFile(EclipseFeatureExtension featureExt) {
    new File(getFeaturesDir(), "${getFeatureId(featureExt)}/feature.xml")
  }

  protected File getPluginsDir() {
    new File(getFeatureOutputDir(), 'plugins')
  }

  protected void writeFeatureBuildPropertiesFile(EclipseFeatureExtension featureExt) {
    File file = getFeatureBuildPropertiesFile(featureExt)
    file.parentFile.mkdirs()
    file.text = 'bin.includes = feature.xml'
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
            plugin id: bundleSymbolicName, 'download-size': '0', 'install-size': '0', version: '0.0.0', unpack: false
          }
          else
            log.error 'Could not add {} to feature {}, because it is not an OSGi bundle', f.name, getFeatureId(featureId)
        }
      }
    }
  }
}

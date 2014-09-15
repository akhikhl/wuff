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

    project.extensions.create('eclipseFeature', EclipseFeatureExtension)

    project.configurations {
      featurePlugin {
        transitive = false
      }
    }

    project.afterEvaluate {

      File featureOutputDir = new File(project.buildDir, 'output')
      File pluginsDir = new File(featureOutputDir, 'plugins')
      File featuresDir = new File(featureOutputDir, 'features')
      File featureXmlFile = new File(featuresDir, "${getFeatureId()}/feature.xml")
      File buildPropertiesFile = new File(featuresDir, "${getFeatureId()}/build.properties")
      String featureOutputFileName = getFeatureId() + '-' + getFeatureVersion() + '.zip'
      File featureAssembleOutputFile = new File(featureOutputDir, 'build.' + getFeatureVersion() + '/' + featureOutputFileName)
      File featureBuildOutputFile = new File(project.buildDir, featureOutputFileName)

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
          featureConfiguration.dependencies.findResults {
            if(it instanceof ProjectDependency) {
              def proj = it.dependencyProject
              proj.tasks.findByName('build')
            }
          }
        }
        inputs.files { featureConfiguration }
        outputs.upToDateWhen {
          Set fileNames = featureConfiguration.files.collect { it.name } as Set
          !pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find { !fileNames.contains(it.name) }
        }
        doLast {
          Set fileNames = featureConfiguration.files.collect { it.name } as Set
          pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).findAll { !fileNames.contains(it.name) }.each {
            it.delete()
          }
        }
      }

      project.task('featureCopyPlugins', type: Copy) {
        group = 'wuff'
        description = 'copies dependency plugins to feature source'
        dependsOn project.tasks.featureRemoveStalePlugins
        inputs.files { featureConfiguration }
        outputs.dir pluginsDir
        from { featureConfiguration.files }
        into pluginsDir
      }

      project.task('featurePrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares eclipse-specific feature files'
        inputs.properties featureId: getFeatureId(),
          featureLabel: getFeatureLabel(),
          featureVersion: getFeatureVersion(),
          featureProviderName: project.extensions.eclipseFeature.providerName,
          featureCopyright: project.extensions.eclipseFeature.copyright,
          featureLicenseUrl: project.extensions.eclipseFeature.licenseUrl,
          featureLicenseText: project.extensions.eclipseFeature.licenseText

        inputs.files { featureConfiguration }
        outputs.file featureXmlFile
        outputs.file buildPropertiesFile
        doLast {
          writeFeatureXml(featureXmlFile)
          buildPropertiesFile.text = 'bin.includes = feature.xml'
        }
      }

      project.task('featureAssemble') {
        group = 'wuff'
        description = 'assembles eclipse feature'
        dependsOn project.tasks.featureCopyPlugins
        dependsOn project.tasks.featurePrepareConfigFiles
        inputs.dir pluginsDir
        inputs.file featureXmlFile
        inputs.file buildPropertiesFile
        outputs.file featureAssembleOutputFile
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
          if(featureAssembleOutputFile.exists())
            featureAssembleOutputFile.delete()
          ExecResult result = project.javaexec {
            main = 'main' // org.eclipse.equinox.launcher.Main
            jvmArgs '-jar', equinoxLauncherPlugin.absolutePath
            jvmArgs '-application', 'org.eclipse.ant.core.antRunner'
            jvmArgs '-buildfile', buildXml.absolutePath
            jvmArgs '-Dbuilder=' + buildConfigDir.absolutePath
            jvmArgs '-DbuildDirectory=' + featureOutputDir.absolutePath
            jvmArgs '-DbaseLocation=' + baseLocation.absolutePath
            jvmArgs '-DtopLevelElementId=' + getFeatureId()
            jvmArgs '-DbuildType=build'
            jvmArgs '-DbuildId=' + getFeatureVersion()
          }
          result.assertNormalExitValue()
        }
      }

      project.task('build', type: Copy) {
        group = 'wuff'
        description = 'builds eclipse feature'
        dependsOn project.tasks.featureAssemble
        inputs.file featureAssembleOutputFile
        outputs.file featureBuildOutputFile
        from featureAssembleOutputFile
        into featureBuildOutputFile.parentFile
      }
    } // afterEvaluate
  }

  protected Configuration getFeatureConfiguration() {
    project.configurations[getFeatureConfigurationName()]
  }

  protected String getFeatureConfigurationName() {
    project.extensions.eclipseFeature.configuration ?: 'featurePlugin'
  }

  protected String getFeatureId() {
    project.extensions.eclipseFeature.id ?: project.name.replace('-', '.')
  }

  protected String getFeatureLabel() {
    project.extensions.eclipseFeature.label ?: project.name
  }

  protected String getFeatureVersion() {
    mavenVersionToEclipseVersion(project.version)
  }

  protected void writeFeatureXml(File file) {
    file.parentFile.mkdirs()
    file.withWriter {
      def xml = new MarkupBuilder(it)
      xml.doubleQuotes = true
      xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
      Map featureAttrs = [ id: getFeatureId(), version: getFeatureVersion() ]
      String featureLabel = getFeatureLabel()
      if(featureLabel)
        featureAttrs.label = featureLabel
      xml.feature featureAttrs, {

        if(featureLabel)
          description featureLabel

        if(project.extensions.eclipseFeature.copyright)
          copyright project.extensions.eclipseFeature.copyright

        if(project.extensions.eclipseFeature.licenseUrl) {
          if(project.extensions.eclipseFeature.licenseText)
            license url: project.extensions.eclipseFeature.licenseUrl, project.extensions.eclipseFeature.licenseText
          else
            license url: project.extensions.eclipseFeature.licenseUrl
        } else if(project.extensions.eclipseFeature.licenseText)
          license project.extensions.eclipseFeature.licenseText

        featureConfiguration.files.each { f ->
          def manifest = ManifestUtils.getManifest(project, f)
          if(ManifestUtils.isBundle(manifest)) {
            String bundleSymbolicName = manifest.mainAttributes?.getValue('Bundle-SymbolicName')
            bundleSymbolicName = bundleSymbolicName.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
            plugin id: bundleSymbolicName, 'download-size': '0', 'install-size': '0', version: '0.0.0', unpack: false
          }
          else
            log.error 'Could not add {} to feature {}, because it is not an OSGi bundle', f.name, getFeatureId()
        }
      }
    }
  }
}


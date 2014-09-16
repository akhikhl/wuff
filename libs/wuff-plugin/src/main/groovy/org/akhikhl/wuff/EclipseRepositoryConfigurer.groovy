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
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import org.gradle.process.ExecResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class EclipseRepositoryConfigurer {

  protected static final Logger log = LoggerFactory.getLogger(EclipseRepositoryConfigurer)

  protected final Project project

  EclipseRepositoryConfigurer(Project project) {
    this.project = project
  }

  void apply() {

    def configurer = new Configurer(project)
    configurer.apply()

    project.wuff.extensions.create('repository', EclipseRepositoryExtension)

    project.configurations {
      repository {
        transitive = false
      }
    }

    project.afterEvaluate {

      File repositoryDir = new File(project.buildDir, getRepositoryId())
      File sourceDir = new File(project.buildDir, 'repository-source')
      File pluginsDir = new File(sourceDir, 'plugins')
      File featuresDir = new File(sourceDir, 'features')
      File categoryXmlFile = new File(sourceDir, 'category.xml')

      File baseLocation = project.effectiveUnpuzzle.eclipseUnpackDir
      def equinoxLauncherPlugin = new File(baseLocation, 'plugins').listFiles({ it.name.matches ~/^org\.eclipse\.equinox\.launcher_(.+)\.jar$/ } as FileFilter)
      if(!equinoxLauncherPlugin)
        throw new GradleException("Could not build feature: equinox launcher not found in ${new File(baseLocation, 'plugins')}")
      equinoxLauncherPlugin = equinoxLauncherPlugin[0]

      if(!project.tasks.findByName('clean'))
        project.task('clean') {
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('repositoryRemoveStaleFeatures') {
        group = 'wuff'
        description = 'removes stale features from repository source'
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        outputs.upToDateWhen {
          Set featureDirNames = (getDependencyFeatureDirs().collect { it.name }) as Set
          !featuresDir.listFiles({ it.isDirectory() } as FileFilter).find({ !featureDirNames.contains(it.name) })
        }
        doLast {
          Set featureDirNames = (getDependencyFeatureDirs().collect { it.name }) as Set
          featuresDir.listFiles({ it.isDirectory() } as FileFilter).find({ !featureDirNames.contains(it.name) }).each {
            it.deleteDir()
          }
        }
      }

      project.task('repositoryRemoveStalePlugins') {
        group = 'wuff'
        description = 'removes stale plugins from repository source'
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        outputs.upToDateWhen {
          Set pluginFileNames = (getDependencyPluginFiles().collect { it.name }) as Set
          !pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find({ !pluginFileNames.contains(it.name) })
        }
        doLast {
          Set pluginFileNames = (getDependencyPluginFiles().collect { it.name }) as Set
          pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find({ !pluginFileNames.contains(it.name) }).each {
            it.delete()
          }
        }
      }

      project.task('repositoryCopyFeatures') {
        group = 'wuff'
        description = 'copies features to repository source'
        dependsOn project.tasks.repositoryRemoveStaleFeatures
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        inputs.files { getDependencyFeatureFiles() }
        outputs.dir featuresDir
        doLast {
          featuresDir.mkdirs()
          for(File sourceFeatureFile in getDependencyFeatureFiles()) {
            File destFeatureDir = new File(featuresDir, sourceFeatureFile.parentFile.name)
            destFeatureDir.mkdirs()
            project.copy {
              from sourceFeatureFile
              into destFeatureDir
            }
          }
        }
      }

      project.task('repositoryCopyPlugins', type: Copy) {
        group = 'wuff'
        description = 'copies plugins to repository source'
        dependsOn project.tasks.repositoryRemoveStalePlugins
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        inputs.files {
          getDependencyPluginFiles()
        }
        outputs.dir pluginsDir
        from { getDependencyPluginFiles() }
        into pluginsDir
      }

      project.task('repositoryPrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares repository configuration files'
        inputs.property 'repositoryId', getRepositoryId()
        inputs.property 'categoryXml', { writeCategoryXmlToString() }
        outputs.file categoryXmlFile
        mustRunAfter project.tasks.repositoryCopyFeatures
        mustRunAfter project.tasks.repositoryCopyPlugins
        doLast {
          writeCategoryXml(categoryXmlFile)
        }
      }

      project.task('repositoryAssemble') {
        group = 'wuff'
        description = 'assembles eclipse repository'
        dependsOn project.tasks.repositoryCopyFeatures
        dependsOn project.tasks.repositoryCopyPlugins
        dependsOn project.tasks.repositoryPrepareConfigFiles
        inputs.dir sourceDir
        outputs.dir repositoryDir
        doLast {
          ExecResult result = project.javaexec {
            main = 'main'
            jvmArgs '-jar', equinoxLauncherPlugin.absolutePath
            jvmArgs '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher'
            jvmArgs '-metadataRepository', repositoryDir.canonicalFile.toURI().toURL().toString()
            jvmArgs '-artifactRepository', repositoryDir.canonicalFile.toURI().toURL().toString()
            jvmArgs '-source', sourceDir.absolutePath
            jvmArgs '-publishArtifacts'
            jvmArgs '-compress'
          }
          result.assertNormalExitValue()
          result = project.javaexec {
            main = 'main'
            jvmArgs '-jar', equinoxLauncherPlugin.absolutePath
            jvmArgs '-application', 'org.eclipse.equinox.p2.publisher.CategoryPublisher'
            jvmArgs '-metadataRepository', repositoryDir.canonicalFile.toURI().toURL().toString()
            jvmArgs '-categoryDefinition', categoryXmlFile.canonicalFile.toURI().toURL().toString()
            jvmArgs '-categoryQualifier'
            jvmArgs '-compress'
          }
          result.assertNormalExitValue()
        }
      }

      if(project.wuff.repository.archive)
        project.task('repositoryArchive', type: Zip) {
          dependsOn project.tasks.repositoryAssemble
          destinationDir = project.buildDir
          if(project.wuff.repository.archiveName) {
            String aname = project.wuff.repository.archiveName
            if(!aname.endsWith('.zip'))
              aname += '.zip'
            archiveName = aname
          }
          else {
            baseName = getRepositoryId()
            if(project.version && project.version != 'unspecified')
              version = project.version
          }
          from repositoryDir
        }

      project.task('build') {
        dependsOn {
          project.wuff.repository.archive ? project.tasks.repositoryArchive : project.tasks.repositoryAssemble
        }
      }
    }
  }

  protected static Iterable<EclipseFeature> collectFeatures(Iterable<File> filesAndDirectories) {
    List features = []
    for(File f in filesAndDirectories)
      collectFeatures(features, f)
    features
  }

  protected static void collectFeatures(Collection<EclipseFeature> features, File f) {
    if(f.isFile() && f.name == 'feature.xml') {
      def featureXml = new XmlSlurper().parse(f)
      features.add(new EclipseFeature(featureXml.id, f))
    } else if(f.isDirectory()) {
      def featureXmlFile = new File(f, 'feature.xml')
      if(featureXmlFile.exists()) {
        def featureXml = new XmlSlurper().parse(featureXmlFile)
        features.add(new EclipseFeature(featureXml.id, featureXmlFile))
      } else {
        def featuresDir = new File(f, 'features')
        if(!featuresDir.exists())
          featuresDir = f
        for(File subdir in featuresDir.listFiles { it.isDirectory() })
          collectFeatures(features, subdir)
      }
    }
  }

  protected Iterable<Project> getDependencyFeatureProjects() {
    repositoryConfiguration.dependencies.findResults {
      if(!(it instanceof ProjectDependency))
        return null
      def proj = it.dependencyProject
      proj.tasks.findByName('featureAssemble') ? proj : null
    }
  }

  protected Set<File> getDependencyFeatureFiles() {
    getDependencyFeatureDirs().collect { new File(it, 'feature.xml') }
  }

  protected Set<File> getDependencyFeatureDirs() {
    Set<File> result = new LinkedHashSet()
    result.addAll getDependencyFeatureProjects().collectMany({ proj ->
      (new File(proj.buildDir, 'output/features').listFiles({ it.isDirectory() && new File(it, 'feature.xml').exists() } as FileFilter) ?: []) as List
    })
    result.addAll getFileDependencies().collectMany({ file ->
      (new File(file, 'features').listFiles({ it.isDirectory() && new File(it, 'feature.xml').exists() } as FileFilter) ?: []) as List
    })
    result
  }

  protected Set<File> getDependencyPluginFiles() {
    Set<File> result = new LinkedHashSet()
    result.addAll getDependencyFeatureProjects().collectMany({ proj ->
      (new File(proj.buildDir, 'output/plugins').listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []) as List
    })
    result.addAll getFileDependencies().collectMany({ file ->
      (new File(file, 'plugins').listFiles({ it.name.endsWith('.jar') || (it.isDirectory() && new File(it, 'MANIFEST.MF').exists()) } as FileFilter) ?: []) as List
    })
    result
  }

  protected Iterable<EclipseFeature> getFeatures(EclipseCategory category) {
    String configurationName = category.configuration ?: 'repository'
    project.configurations[configurationName].dependencies.collectMany { dep ->
      if(dep instanceof ProjectDependency)
        return [ new EclipseFeature(EclipseFeatureConfigurer.getFeatureId(dep.dependencyProject)) ]
      if(dep instanceof FileCollectionDependency)
        return collectFeatures(dep.resolve())
      []
    }
  }

  protected Set<File> getFileDependencies() {
    repositoryConfiguration.dependencies.collectMany {
      it instanceof FileCollectionDependency ? it.resolve() : []
    }
  }

  protected Configuration getRepositoryConfiguration() {
    project.configurations[getRepositoryConfigurationName()]
  }

  protected String getRepositoryConfigurationName() {
    project.wuff.repository.configuration ?: 'repository'
  }

  protected String getRepositoryId() {
    project.wuff.repository.id ?: project.name.replace('-', '.')
  }

  protected void writeCategoryXml(File file) {
    file.parentFile.mkdirs()
    file.withWriter {
      writeCategoryXml(it)
    }
  }

  protected void writeCategoryXml(Writer writer) {
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.site {

      Map descriptionAttrs = [:]
      if(project.wuff.repository.url)
        descriptionAttrs.url = project.wuff.repository.url

      if(descriptionAttrs || project.wuff.repository.description)
        description descriptionAttrs, project.wuff.repository.description

      Map featuresToCategories = [:]

      for(def categoryDef in project.wuff.repository.categories) {
        for(def featureDef in getFeatures(categoryDef)) {
          featuresToCategories[featureDef.id] = categoryDef.name
        }
      }

      for(def e in featuresToCategories)
        feature id: e.key, version: '0.0.0', {
          category name: e.value
        }

      for(def categoryDef in project.wuff.repository.categories) {
        Map categoryDefAttrs = [ name: categoryDef.name ]
        if(categoryDef.label)
          categoryDefAttrs.label = categoryDef.label
        'category-def' categoryDefAttrs, {
          if(categoryDef.description)
            description categoryDef.description
        }
      }
    }
  }

  protected String writeCategoryXmlToString() {
    def writer = new StringWriter()
    writeCategoryXml(writer)
    writer.toString()
  }
}

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
import org.gradle.api.Task
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
  protected timeStamp

  EclipseRepositoryConfigurer(Project project) {
    this.project = project
  }

  void apply() {

    if(!project.extensions.findByName('wuff')) {
      def configurer = new Configurer(project)
      configurer.apply()
    }

    if(project.wuff.extensions.findByName('repository')) {
      log.warn 'Attempt to apply {} more than once on project {}', this.getClass().getName(), project
      return
    }

    project.wuff.extensions.create('repository', EclipseRepositoryExtension)

    project.wuff.extensions.create('repositories', EclipseRepositoriesExtension)
    project.wuff.repositories.repositoriesMap[''] = project.wuff.repository

    project.configurations {
      repository {
        transitive = false
      }
    }

    project.afterEvaluate {

      //File repositoryDir = new File(project.buildDir, getRepositoryId())
      //File sourceDir = new File(project.buildDir, 'repository-source')
      //File pluginsDir = new File(sourceDir, 'plugins')
      //File featuresDir = new File(sourceDir, 'features')
      //File categoryXmlFile = new File(sourceDir, 'category.xml')

      File baseLocation = project.effectiveUnpuzzle.eclipseUnpackDir
      def equinoxLauncherPlugin = new File(baseLocation, 'plugins').listFiles({ it.name.matches ~/^org\.eclipse\.equinox\.launcher_(.+)\.jar$/ } as FileFilter)
      if(!equinoxLauncherPlugin)
        throw new GradleException("Could not build feature: equinox launcher not found in ${new File(baseLocation, 'plugins')}")
      equinoxLauncherPlugin = equinoxLauncherPlugin[0]

      if(!project.tasks.findByName('clean'))
        project.task('clean') {
          group = 'wuff'
          description = "deletes directory ${project.buildDir}"
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('repositoryRemoveStaleFeatures') {
        group = 'wuff'
        description = 'removes stale features from repository'
        dependsOn { getFeatureArchiveTasks() }
        input.files { getFeatureArchiveFiles() }
        outputs.upToDateWhen {
          getRepositories().every { repositoryExt ->
            Set fileNames = getFeatureArchiveFiles(repositoryExt).collect { it.name }
            getRepositoryTempFeatureArchiveFiles(repositoryExt).every { fileNames.contains(it.name) }
          }
        }
        doLast {
          getRepositories().each { repositoryExt ->
            Set fileNames = getFeatureArchiveFiles(repositoryExt).collect { it.name }
            getRepositoryTempFeatureArchiveFiles(repositoryExt).findAll { !fileNames.contains(it.name) }.each {
              it.delete()
            }
          }
        }
      }

      project.task('repositoryRemoveStalePlugins') {
        group = 'wuff'
        description = 'removes stale plugins from repository'
        dependsOn { getPluginJarTasks() }
        inputs.files { getPluginFiles() }
        outputs.upToDateWhen {
          getRepositories().every { repositoryExt ->
            Set fileNames = getPluginFiles(repositoryExt).collect { it.name }
            getRepositoryTempPluginFiles(repositoryExt).every { fileNames.contains(it.name) }
          }
        }
        doLast {
          getRepositories().each { repositoryExt ->
            Set fileNames = getPluginFiles(repositoryExt).collect { it.name }
            getRepositoryTempPluginFiles(repositoryExt).findAll { !fileNames.contains(it.name) }.each {
              it.delete()
            }
          }
        }
      }

      project.task('repositoryCopyFeatures') {
        group = 'wuff'
        description = 'copies features to repository'
        dependsOn project.tasks.repositoryRemoveStaleFeatures
        input.files { getFeatureArchiveFiles() }
        outputs.files { getRepositoryTempFeatureArchiveFiles() }
        doLast {
          getRepositories().each { repositoryExt ->
            File destDir = getRepositoryTempFeaturesDir(repositoryExt)
            destDir.mkdirs()
            getFeatureArchiveFiles(repositoryExt).each {
              FileUtils.copyFileToDirectory(it, destDir)
            }
          }
        }
      }

      project.task('repositoryCopyPlugins') {
        group = 'wuff'
        description = 'copies plugins to repository'
        dependsOn project.tasks.repositoryRemoveStalePlugins
        inputs.files { getPluginFiles() }
        outputs.files { getRepositoryTempPluginFiles() }
        doLast {
          getRepositories().each { repositoryExt ->
            File destDir = getRepositoryTempPluginsDir(repositoryExt)
            destDir.mkdirs()
            getPluginFiles(repositoryExt).each {
              FileUtils.copyFileToDirectory(it, destDir)
            }
          }
        }
      }

      project.task('repositoryPrepareConfigFiles') {
        group = 'wuff'
        description = 'prepares repository configuration files'
        mustRunAfter project.tasks.repositoryCopyFeatures
        mustRunAfter project.tasks.repositoryCopyPlugins
        inputs.property 'repositoriesProperties', {
          getNonEmptyRepositories().collect { repositoryExt ->
            [ repositoryId: getRepositoryId(repositoryExt),
              repositoryVersion: getRepositoryVersion(repositoryExt) ]
          }
        }
        inputs.property 'categoryXml', { writeCategoryXmlToString() }
        outputs.files { getRepositoryTempCategoryXmlFiles() }
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            writeCategoryXml(repositoryExt)
          }
        }
      }

      project.task('repositoryAssemble') {
        group = 'wuff'
        description = 'assembles eclipse repository'
        dependsOn project.tasks.repositoryCopyFeatures
        dependsOn project.tasks.repositoryCopyPlugins
        dependsOn project.tasks.repositoryPrepareConfigFiles
        inputs.dir getRepositoryTempBaseDir()
        outputs.dir getRepositoryTemp2BaseDir()
        doLast {
          getNonEmptyRepositories().each {
            File sourceDir = getRepositoryTempDir(it)
            File repositoryDir = getRepositoryTemp2Dir(it)
            File categoryXmlFile = getRepositoryTempCategoryXmlFile(it)
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

  List<File> getFeatureArchiveFiles() {
    getRepositories().collectMany { getFeatureArchiveFiles(it) }
  }

  List<File> getFeatureArchiveFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany { new EclipseFeatureConfigurer(it).getFeatureArchiveFiles() }
  }

  List<Task> getFeatureArchiveTasks() {
    getRepositories().collectMany { getFeatureArchiveTasks(it) }
  }

  List<Task> getFeatureArchiveTasks(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collect { it.tasks.featureArchive }
  }

  Iterable<Project> getFeatureProjects() {
    getRepositories().collectMany { getFeatureProjects(it) }
  }

  Iterable<Project> getFeatureProjects(EclipseRepositoryExtension repositoryExt) {
    getRepositoryConfiguration(repositoryExt).dependencies.findResults {
      if(it instanceof ProjectDependency) {
        def proj = it.dependencyProject
        EclipseFeatureConfigurer.isFeatureProject(proj) ? proj : null
      }
    }
  }

  List<EclipseRepositoryExtension> getNonEmptyRepositories() {
    project.wuff.repositories.repositoriesMap.values().findAll { hasFeaturesAndPluginFiles(it) }
  }

  List<File> getPluginFiles() {
    getRepositories().collectMany { getPluginFiles(it) }
  }

  List<File> getPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany {
      new EclipseFeatureConfigurer(it).getPluginFiles()
    }
  }

  List<Task> getPluginJarTasks() {
    getRepositories().collectMany { getPluginJarTasks(it) }
  }

  List<Task> getPluginJarTasks(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany {
      new EclipseFeatureConfigurer(it).getPluginJarTasks()
    }
  }

  List<EclipseRepositoryExtension> getRepositories() {
    project.wuff.repositories.repositoriesMap.values()
  }

  Configuration getRepositoryConfiguration(EclipseRepositoryExtension repositoryExt) {
    project.configurations[repositoryExt.configuration ?: 'repository']
  }

  String getRepositoryId(EclipseRepositoryExtension repositoryExt) {
    repositoryExt.id ?: project.name.replace('-', '.')
  }

  File getRepositoryOutputDir() {
    new File(project.buildDir, 'repository-output')
  }

  File getRepositoryTempBaseDir() {
    new File(project.buildDir, 'repository-temp')
  }

  File getRepositoryTempCategoryXmlFiles() {
    getNonEmptyRepositories().collect { getRepositoryTempCategoryXmlFile(it) }
  }

  File getRepositoryTempCategoryXmlFile(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'category.xml')
  }

  File getRepositoryTempDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempBaseDir(), getRepositoryId(repositoryExt) + '_' + getRepositoryVersion(repositoryExt))
  }

  List<File> getRepositoryTempFeatureArchiveFiles() {
    getRepositories().collectMany { getRepositoryTempFeatureArchiveFiles(it) }
  }

  List<File> getRepositoryTempFeatureArchiveFiles(EclipseRepositoryExtension repositoryExt) {
    getRepositoryTempFeaturesDir(repositoryExt).listFiles({ it.name.endsWith('.jar') } as FileFilter)
  }

  File getRepositoryTempFeaturesDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'features')
  }

  List<File> getRepositoryTempPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getRepositoryTempPluginsDir(repositoryExt).listFiles({ it.name.endsWith('.jar') } as FileFilter)
  }

  File getRepositoryTempPluginsDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'plugins')
  }

  File getRepositoryTemp2BaseDir() {
    new File(project.buildDir, 'repository-temp2')
  }

  File getRepositoryTemp2Dir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTemp2BaseDir(), getRepositoryId(repositoryExt) + '_' + getRepositoryVersion(repositoryExt))
  }

  String getRepositoryVersion(EclipseRepositoryExtension repositoryExt) {
    mavenVersionToEclipseVersion(repositoryExt.version ?: project.version)
  }

  boolean hasFeaturesAndPluginFiles() {
    getRepositories().any { hasFeaturesAndPluginFiles(it) }
  }

  boolean hasFeaturesAndPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).any {
      new EclipseFeatureConfigurer(it).hasPluginFiles()
    }
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

  void writeCategoryXml(File file) {
    file.parentFile.mkdirs()
    file.withWriter {
      writeCategoryXml(it)
    }
  }

  void writeCategoryXml(Writer writer) {
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

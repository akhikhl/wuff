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

    def defaultRepositoryConfig = new EclipseRepositoryExtension(
      id: project.name.replace('-', '.'),
      version: (!project.version || project.version == 'unspecified') ? '1.0.0' : project.version
    )
    defaultRepositoryConfig.category project.name.replace('-', '.')
    defaultRepositoryConfig.archiveFileName = { EclipseRepositoryExtension repositoryExt ->
      "${repositoryExt.id}_${repositoryExt.version}.zip"
    }

    project.wuff.extensions.create('repository', EclipseRepositoryExtension)
    project.wuff.repository.defaultConfig = defaultRepositoryConfig

    project.wuff.extensions.create('repositories', EclipseRepositoriesExtension)
    project.wuff.repositories.defaultConfig = defaultRepositoryConfig
    project.wuff.repositories.repositoryList.add(project.wuff.repository)

    project.configurations {
      repository {
        transitive = false
      }
    }

    project.afterEvaluate {

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
        inputs.files { getFeatureArchiveFiles() }
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
        inputs.files { getFeatureArchiveFiles() }
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
            [ repositoryId: repositoryExt.id,
              repositoryVersion: repositoryExt.version,
              categoryXml: writeCategoryXmlString(repositoryExt) ]
          }
        }
        outputs.files { getRepositoryTempCategoryXmlFiles() }
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            writeCategoryXmlFile(repositoryExt)
          }
        }
      }

      project.task('repositoryAssemble') {
        group = 'wuff'
        description = 'assembles eclipse repository'
        dependsOn project.tasks.repositoryCopyFeatures
        dependsOn project.tasks.repositoryCopyPlugins
        dependsOn project.tasks.repositoryPrepareConfigFiles
        inputs.property 'repositoriesProperties', {
          getNonEmptyRepositories().collect { repositoryExt ->
            [ repositoryId: repositoryExt.id,
              repositoryVersion: repositoryExt.version ]
          }
        }
        inputs.dir getRepositoryTempBaseDir()
        outputs.dir getRepositoryOutputUnpackedBaseDir()
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            File sourceDir = getRepositoryTempDir(repositoryExt)
            File repositoryDir = getRepositoryOutputUnpackedDir(repositoryExt)
            File categoryXmlFile = getRepositoryTempCategoryXmlFile(repositoryExt)
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

      project.task('repositoryArchive') {
        dependsOn project.tasks.repositoryAssemble
        inputs.dir getRepositoryOutputUnpackedBaseDir()
        outputs.files {
          getNonEmptyRepositories().findResults { repositoryExt ->
            if(repositoryExt.enableArchive)
              getRepositoryOutputArchiveFile(repositoryExt)
          }
        }
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            if(repositoryExt.enableArchive)
              ArchiveUtils.zip getRepositoryOutputArchiveFile(repositoryExt), {
                from getRepositoryOutputUnpackedDir(repositoryExt), {
                  add '.'
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

      project.tasks.build.dependsOn project.tasks.repositoryArchive
    }
  }

  Configuration getConfiguration(EclipseCategory category) {
    project.configurations[category.configuration ?: 'repository']
  }

  Collection<Configuration> getConfigurations(EclipseRepositoryExtension repositoryExt) {
    repositoryExt.categories.collect { getConfiguration(it) }
  }

  Collection<File> getFeatureArchiveFiles() {
    getRepositories().collectMany { getFeatureArchiveFiles(it) }
  }

  Collection<File> getFeatureArchiveFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany { new EclipseFeatureConfigurer(it).getFeatureArchiveFiles() }
  }

  Collection<Task> getFeatureArchiveTasks() {
    getRepositories().collectMany { getFeatureArchiveTasks(it) }
  }

  Collection<Task> getFeatureArchiveTasks(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collect { it.tasks.featureArchive }
  }

  Iterable<Project> getFeatureProjects() {
    getRepositories().collectMany { getFeatureProjects(it) }
  }

  Iterable<Project> getFeatureProjects(EclipseRepositoryExtension repositoryExt) {
    getConfigurations(repositoryExt).collectMany { config ->
      config.dependencies.findResults {
        if(it instanceof ProjectDependency) {
          def proj = it.dependencyProject
          EclipseFeatureConfigurer.isFeatureProject(proj) ? proj : null
        }
      }
    }
  }

  protected Iterable<EclipseFeatureExtension> getFeaturesForCategory(EclipseCategory category) {
    getConfiguration(category).dependencies.collectMany { dep ->
      if(dep instanceof ProjectDependency) {
        def proj = dep.dependencyProject
        if(EclipseFeatureConfigurer.isFeatureProject(proj))
          return new EclipseFeatureConfigurer(proj).getNonEmptyFeatures()
      }
      []
    }
  }

  Collection<EclipseRepositoryExtension> getNonEmptyRepositories() {
    getRepositories().findAll { hasFeaturesAndPluginFiles(it) }
  }

  Collection<File> getPluginFiles() {
    getRepositories().collectMany { getPluginFiles(it) }
  }

  Collection<File> getPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany {
      new EclipseFeatureConfigurer(it).getPluginFiles()
    }
  }

  Collection<Task> getPluginJarTasks() {
    getRepositories().collectMany { getPluginJarTasks(it) }
  }

  Collection<Task> getPluginJarTasks(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).collectMany {
      new EclipseFeatureConfigurer(it).getPluginJarTasks()
    }
  }

  Collection<EclipseRepositoryExtension> getRepositories() {
    project.wuff.repositories.repositoryList
  }

  File getRepositoryOutputArchiveFile(EclipseRepositoryExtension repositoryExt) {
    def archiveFileName = repositoryExt.archiveFileName
    if(archiveFileName instanceof Closure)
      archiveFileName = archiveFileName(repositoryExt)
    new File(getRepositoryOutputBaseDir(), archiveFileName.toString())
  }

  File getRepositoryOutputBaseDir() {
    new File(project.buildDir, 'repository-output')
  }

  File getRepositoryOutputUnpackedBaseDir() {
    new File(project.buildDir, 'repository-output-unpacked')
  }

  File getRepositoryOutputUnpackedDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryOutputUnpackedBaseDir(), repositoryExt.id + '_' + repositoryExt.version)
  }

  File getRepositoryTempBaseDir() {
    new File(project.buildDir, 'repository-temp')
  }

  Collection<File> getRepositoryTempCategoryXmlFiles() {
    getNonEmptyRepositories().collect { getRepositoryTempCategoryXmlFile(it) }
  }

  File getRepositoryTempCategoryXmlFile(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'category.xml')
  }

  File getRepositoryTempDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempBaseDir(), repositoryExt.id + '_' + repositoryExt.version)
  }

  Collection<File> getRepositoryTempFeatureArchiveFiles() {
    getRepositories().collectMany { getRepositoryTempFeatureArchiveFiles(it) }
  }

  Collection<File> getRepositoryTempFeatureArchiveFiles(EclipseRepositoryExtension repositoryExt) {
    getRepositoryTempFeaturesDir(repositoryExt).listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []
  }

  File getRepositoryTempFeaturesDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'features')
  }

  Collection<File> getRepositoryTempPluginFiles() {
    getRepositories().collectMany { getRepositoryTempPluginFiles(it) }
  }

  Collection<File> getRepositoryTempPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getRepositoryTempPluginsDir(repositoryExt).listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []
  }

  File getRepositoryTempPluginsDir(EclipseRepositoryExtension repositoryExt) {
    new File(getRepositoryTempDir(repositoryExt), 'plugins')
  }

  boolean hasFeaturesAndPluginFiles() {
    getRepositories().any { hasFeaturesAndPluginFiles(it) }
  }

  boolean hasFeaturesAndPluginFiles(EclipseRepositoryExtension repositoryExt) {
    getFeatureProjects(repositoryExt).any {
      new EclipseFeatureConfigurer(it).hasPluginFiles()
    }
  }

  void writeCategoryXml(EclipseRepositoryExtension repositoryExt, Writer writer) {
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.site {

      Map descriptionAttrs = [:]
      if(repositoryExt.url)
        descriptionAttrs.url = repositoryExt.url

      if(descriptionAttrs || repositoryExt.description)
        description descriptionAttrs, repositoryExt.description

      Map featureMap = [:]

      for(EclipseCategory categoryDef in repositoryExt.categories) {
        for(EclipseFeatureExtension featureExt in getFeaturesForCategory(categoryDef)) {
          featureMap[featureExt.id] = [ version: '0.0.0', category: categoryDef.name ]
        }
      }

      for(def e in featureMap)
        feature id: e.key, version: e.value.version, {
          category name: e.value.category
        }

      for(EclipseCategory categoryDef in repositoryExt.categories) {
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

  String writeCategoryXmlFile(EclipseRepositoryExtension repositoryExt) {
    File file = getRepositoryTempCategoryXmlFile(repositoryExt)
    file.parentFile.mkdirs()
    file.withWriter {
      writeCategoryXml(repositoryExt, it)
    }
  }

  String writeCategoryXmlString(EclipseRepositoryExtension repositoryExt) {
    def writer = new StringWriter()
    writeCategoryXml(repositoryExt, writer)
    writer.toString()
  }
}

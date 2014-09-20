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

  static boolean isRepositoryProject(Project proj) {
    proj != null && proj.extensions.findByName('wuff') && proj.wuff.ext.has('repositoryList')
  }

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

    if(project.wuff.ext.has('repositoryList')) {
      log.warn 'Attempt to apply {} more than once on project {}', this.getClass().getName(), project
      return
    }

    project.wuff.ext.repositoryList = []
    project.wuff.ext.defaultRepositoryList = []
    
    project.wuff.metaClass {
      repository = { Object... args ->
        String id
        Closure closure
        for(def arg in args)
          if(arg instanceof String)
            id = arg
          else if(arg instanceof Closure)
            closure = arg
        if(!id)
          id = EclipseRepository.getDefaultId(project)
        def f = project.wuff.ext.repositoryList.find { it.id == id }
        if(f == null) {
          f = new EclipseRepository(project, id)
          project.wuff.ext.repositoryList.add(f)
        }
        if(closure != null) {
          closure.delegate = f
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure()
        }
      }
    }

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
            Set fileNames = repositoryExt.getFeatureArchiveFiles().collect { it.name }
            repositoryExt.getTempFeatureArchiveFiles().every { fileNames.contains(it.name) }
          }
        }
        doLast {
          getRepositories().each { repositoryExt ->
            Set fileNames = repositoryExt.getFeatureArchiveFiles().collect { it.name }
            repositoryExt.getTempFeatureArchiveFiles().findAll { !fileNames.contains(it.name) }.each {
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
            Set fileNames = repositoryExt.getPluginFiles().collect { it.name }
            repositoryExt.getTempPluginFiles().every { fileNames.contains(it.name) }
          }
        }
        doLast {
          getRepositories().each { repositoryExt ->
            Set fileNames = repositoryExt.getPluginFiles().collect { it.name }
            repositoryExt.getTempPluginFiles().findAll { !fileNames.contains(it.name) }.each {
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
        outputs.files { getTempFeatureArchiveFiles() }
        doLast {
          getRepositories().each { repositoryExt ->
            File destDir = repositoryExt.getTempFeaturesDir()
            destDir.mkdirs()
            repositoryExt.getFeatureArchiveFiles().each {
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
        outputs.files { getTempPluginFiles() }
        doLast {
          getRepositories().each { repositoryExt ->
            File destDir = repositoryExt.getTempPluginsDir()
            destDir.mkdirs()
            repositoryExt.getPluginFiles().each {
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
        outputs.files { getTempCategoryXmlFiles() }
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
        inputs.dir EclipseRepository.getTempBaseDir(project)
        outputs.dir EclipseRepository.getOutputUnpackedBaseDir(project)
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            File sourceDir = repositoryExt.getTempDir()
            File repositoryDir = repositoryExt.getOutputUnpackedDir()
            File categoryXmlFile = repositoryExt.getTempCategoryXmlFile()
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
        inputs.dir EclipseRepository.getOutputUnpackedBaseDir(project)
        outputs.files {
          getNonEmptyRepositories().findResults { repositoryExt ->
            if(repositoryExt.enableArchive)
              repositoryExt.getArchiveFile()
          }
        }
        doLast {
          getNonEmptyRepositories().each { repositoryExt ->
            if(repositoryExt.enableArchive)
              ArchiveUtils.zip repositoryExt.getArchiveFile(), {
                from repositoryExt.getOutputUnpackedDir(), {
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

  Collection<File> getFeatureArchiveFiles() {
    getRepositories().collectMany { it.getFeatureArchiveFiles() }
  }

  Collection<Task> getFeatureArchiveTasks() {
    getRepositories().collectMany { it.getFeatureArchiveTasks() }
  }

  Iterable<Project> getFeatureProjects() {
    getRepositories().collectMany({ it.getFeatureProjects() }).unique(false)
  }

  Iterable<EclipseFeature> getFeatures() {
    getRepositories().collectMany { it.getFeatures() }
  }

  Collection<EclipseRepository> getNonEmptyRepositories() {
    getRepositories().findAll { it.hasFeaturesAndPluginFiles() }
  }

  Collection<File> getPluginFiles() {
    getRepositories().collectMany { it.getPluginFiles() }
  }

  Collection<Task> getPluginJarTasks() {
    getRepositories().collectMany { it.getPluginJarTasks() }
  }

  Collection<EclipseRepository> getRepositories() {
    def result = project.wuff.ext.repositoryList
    if(!result) {
      result = project.wuff.ext.defaultRepositoryList
      if(!result)
        result = project.wuff.ext.defaultRepositoryList = [ new EclipseRepository(project, null) ]
    }
    result
  }

  Collection<File> getTempCategoryXmlFiles() {
    getNonEmptyRepositories().collect { it.getTempCategoryXmlFile() }
  }

  Collection<File> getTempFeatureArchiveFiles() {
    getRepositories().collectMany { it.getTempFeatureArchiveFiles() }
  }

  Collection<File> getTempPluginFiles() {
    getRepositories().collectMany { it.getTempPluginFiles() }
  }

  boolean hasFeaturesAndPluginFiles() {
    getRepositories().any { it.hasFeaturesAndPluginFiles() }
  }

  void writeCategoryXml(EclipseRepository repositoryExt, Writer writer) {
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

      for(EclipseCategory categoryDef in repositoryExt.categories)
        for(EclipseFeature featureExt in repositoryExt.getFeaturesForCategory(categoryDef))
          featureMap[featureExt.id] = [ version: '0.0.0', category: categoryDef.name ]

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

  String writeCategoryXmlFile(EclipseRepository repositoryExt) {
    File file = repositoryExt.getTempCategoryXmlFile()
    file.parentFile.mkdirs()
    file.withWriter {
      writeCategoryXml(repositoryExt, it)
    }
  }

  String writeCategoryXmlString(EclipseRepository repositoryExt) {
    def writer = new StringWriter()
    writeCategoryXml(repositoryExt, writer)
    writer.toString()
  }
}

/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class EclipseRepository {
  
  static String getDefaultId(Project proj) {
    proj.name.replace('-', '.')
  }

  static File getOutputBaseDir(Project proj) {
    new File(proj.buildDir, 'repository-output')
  }

  static File getOutputUnpackedBaseDir(Project proj) {
    new File(proj.buildDir, 'repository-output-unpacked')
  }

  static File getTempBaseDir(Project proj) {
    new File(proj.buildDir, 'repository-temp')
  }

  protected static final Logger log = LoggerFactory.getLogger(EclipseRepository)

  final Project project
  private final String id
  String version
  String url
  String description
  private final List<EclipseCategory> categories = []

  // when enableArchive is false, repository is created, but not archived
  boolean enableArchive = true

  // you can assign archive name to a string or to a closure
  def archiveFileName
  
  EclipseRepository(Project project, String id) {
    this.project = project
    this.id = id
  }

  void category(String name, Closure closure = null) {
    assert name != null
    assert !name.isEmpty()
    def f = categories.find { it.name == name }
    if(f == null) {
      f = new EclipseCategory(name)
      categories.add(f)
    }
    if(closure != null) {
      closure.delegate = f
      closure.resolveStrategy = Closure.DELEGATE_FIRST
      closure()
    }
  }
  
  def getArchiveFileName() {
    def result = archiveFileName ?: {
      "${getId()}_${getVersion()}.zip"
    }
    if(result instanceof Closure)
      result = result()
    result.toString()
  }

  File getArchiveFile() {
    new File(getOutputBaseDir(), getArchiveFileName())
  }

  String getId() {
    id ?: getDefaultId(project)
  }

  Collection<EclipseCategory> getCategories() {
    if(!categories)
      category project.name.replace('-', '.')
    categories
  }

  Collection<Configuration> getConfigurations() {
    getCategories().collect { getConfigurationForCategory(it) }
  }

  Configuration getConfigurationForCategory(EclipseCategory category) {
    project.configurations[category.configuration ?: 'repository']
  }

  Collection<File> getFeatureArchiveFiles() {
    getFeatures().collect { it.getArchiveFile() }
  }

  Collection<Task> getFeatureArchiveTasks() {
    getFeatureProjects().collect { it.tasks.featureArchive }
  }

  Iterable<Project> getFeatureProjects() {
    categories.collectMany({ getFeatureProjectsForCategory(it) }).unique(false)
  }

  Iterable<Project> getFeatureProjectsForCategory(EclipseCategory category) {
    List result = []
    result.addAll getConfigurationForCategory(category).dependencies.findResults({ dep ->
      if(dep instanceof ProjectDependency) {
        def proj = dep.dependencyProject
        return EclipseFeatureConfigurer.isFeatureProject(proj) ? proj : null
      }
    })
    result.addAll category.features.findResults({ featureId ->
      def featureExt
      if(EclipseFeatureConfigurer.isFeatureProject(project)) {
        featureExt = project.wuff.ext.featureList.find { it.id == featureId }
        if(!featureExt)
          log.warn 'Could not find feature {} in {}.', featureId, project
      } else {
        log.warn 'Could not add feature {} to category {} since {} does not implement features.', featureId, category.name, project
        log.warn 'Please make sure you applied \'org.akhikhl.wuff.eclipse-feature\''
      }
      if(featureExt)
        project
    })
    result.unique(false)
  }

  Iterable<EclipseFeature> getFeatures() {
    categories.collectMany { getFeaturesForCategory(it) }
  }

  Iterable<EclipseFeature> getFeaturesForCategory(EclipseCategory category) {
    List result = []
    result.addAll getConfigurationForCategory(category).dependencies.collectMany({ dep ->
      if(dep instanceof ProjectDependency) {
        def proj = dep.dependencyProject
        if(EclipseFeatureConfigurer.isFeatureProject(proj))
          return new EclipseFeatureConfigurer(proj).getNonEmptyFeatures()
      }
      []
    })
    result.addAll category.features.findResults({ featureId ->
      def featureExt
      if(EclipseFeatureConfigurer.isFeatureProject(project)) {
        featureExt = project.wuff.ext.featureList.find { it.id == featureId }
        if(!featureExt)
          log.warn 'Could not find feature {} in {}.', featureId, project
      } else {
        log.warn 'Could not add feature {} to category {} since {} does not implement features.', featureId, category.name, project
        log.warn 'Please make sure you applied \'org.akhikhl.wuff.eclipse-feature\''
      }
      featureExt
    })
    result
  }

  Collection<File> getPluginFiles() {
    getFeatures().collectMany { it.getPluginFiles() }
  }

  Collection<Task> getPluginJarTasks() {
    getFeatures().collectMany { it.getPluginJarTasks() }
  }

  File getOutputBaseDir() {
    getOutputBaseDir(project)
  }

  File getOutputUnpackedDir() {
    new File(getOutputUnpackedBaseDir(project), getId() + '_' + getVersion())
  }

  File getTempDir() {
    new File(getTempBaseDir(project), getId() + '_' + getVersion())
  }

  File getTempCategoryXmlFile() {
    new File(getTempDir(), 'category.xml')
  }

  Collection<File> getTempFeatureArchiveFiles() {
    getTempFeaturesDir().listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []
  }

  File getTempFeaturesDir() {
    new File(getTempDir(), 'features')
  }

  Collection<File> getTempPluginFiles() {
    getTempPluginsDir().listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []
  }

  File getTempPluginsDir() {
    new File(getTempDir(), 'plugins')
  }
  
  String getVersion() {
    version ?: ((!project.version || project.version == 'unspecified') ? '1.0.0' : project.version)
  }

  boolean hasFeaturesAndPluginFiles() {
    getFeatures().any { it.hasPluginFiles() }
  }
  
  void setArchiveFileName(newValue) {
    archiveFileName = newValue
  }
  
  void setVersion(String newValue) {
    version = newValue
  }
}

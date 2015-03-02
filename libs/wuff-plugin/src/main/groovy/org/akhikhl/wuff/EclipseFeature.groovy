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

/**
 *
 * @author akhikhl
 */
class EclipseFeature {
  
  static String getDefaultId(Project proj) {
    proj.name.replace('-', '.')
  }

  final Project project
  private final String id
  private String version
  private String label
  String providerName
  String copyright
  String licenseUrl
  String licenseText
  String configuration
  
  EclipseFeature(Project project, String id) {
    this.project = project
    this.id = id
  }

  File getArchiveFile() {
    new File(getOutputDir(), getId() + '_' + getVersion() + '.jar')
  }

  String getConfigurationName() {
    configuration ?: 'feature'
  }

  Configuration getConfiguration() {
    project.configurations[getConfigurationName()]
  }

  String getId() {
    id ?: getDefaultId(project)
  }

  String getLabel() {
    label ?: getId()
  }
  
  File getOutputDir() {
    new File(project.buildDir, 'feature-output')
  }

  Collection<File> getPluginFiles() {
    getConfiguration().files
  }

  Collection<Task> getPluginJarTasks() {
    getConfiguration().dependencies.findResults { dep ->
      dep instanceof ProjectDependency ? dep.dependencyProject.tasks.findByName('jar') : null
    }
  }

  File getTempDir() {
    new File(project.buildDir, 'feature-temp/' + getId() + '_' + getVersion())
  }

  File getTempFeatureXmlFile() {
    new File(getTempDir(), 'feature.xml')
  }
  
  String getVersion() {
    version ?: ((!project.version || project.version == 'unspecified') ? '1.0.0' : project.version)
  }

  boolean hasPluginFiles() {
    !getConfiguration().isEmpty()
  }
  
  void setConfiguration(String newValue) {
    configuration = newValue
  }
  
  void setLabel(String newValue) {
    label = newValue
  }
  
  void setOutputDir(File newValue) {
    outputDir = newValue
  }
  
  void setVersion(String newValue) {
    version = newValue
  }
}

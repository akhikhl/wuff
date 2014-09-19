/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class EclipseRepositoryExtension {

  String id
  String version
  String url
  String description
  private final List<EclipseCategory> categories = []

  // when enableArchive is false, repository is created, but not archived
  boolean enableArchive = true

  // you can reassign archive name to a string or to closure
  def archiveFileName
  
  String defaultCategoryName
	EclipseRepositoryExtension defaultConfig

  void category(String name, Closure closure = null) {
    if(name == null)
      name = defaultCategoryName ?: ''
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
    archiveFileName ?: defaultConfig?.archiveFileName
  }

  String getId() {
    id ?: defaultConfig?.id
  }

  Collection<EclipseCategory> getCategories() {
    categories ?: defaultConfig?.categories
  }
  
  String getVersion() {
    version ?: defaultConfig?.version
  }
  
  void setArchiveFileName(newValue) {
    archiveFileName = newValue
  }
  
  void setId(String newValue) {
    id = newValue
  }
  
  void setVersion(String newValue) {
    version = newValue
  }
}

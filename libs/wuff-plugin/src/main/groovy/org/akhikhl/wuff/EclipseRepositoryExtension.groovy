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

  private final String id
  String version
  String url
  String description
  private final List<EclipseCategory> categories = []

  // when enableArchive is false, repository is created, but not archived
  boolean enableArchive = true

  // you can reassign archive name to a string or to closure
  def archiveFileName
  
	final EclipseRepositoryExtension defaultConfig
  
  EclipseRepositoryExtension(String id, EclipseRepositoryExtension defaultConfig) {
    this.id = id
    this.defaultConfig = defaultConfig
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
  
  void setVersion(String newValue) {
    version = newValue
  }
}

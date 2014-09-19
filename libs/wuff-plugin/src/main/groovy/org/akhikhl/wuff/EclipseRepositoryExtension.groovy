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

  static final Closure defaultArchiveFileName = { String repositoryId, String repositoryVersion ->
    "${repositoryId}_${repositoryVersion}.zip"
  }

  String id
  String version
  String url
  String description
  private final List<EclipseCategory> categories = []

  // when enableArchive is false, repository is created, but not archived
  boolean enableArchive = true

  // you can reassign archive name to a string or to another closure
  def archiveFileName = defaultArchiveFileName

  void category(String name) {
    categories.add(new EclipseCategory(name))
  }

  Collection<EclipseCategory> getCategories(Project proj) {
    if(categories.isEmpty()) {
      String name = description ?: id ?: proj.name.replace('-', '.')
      category(name)
    }
    categories
  }
}

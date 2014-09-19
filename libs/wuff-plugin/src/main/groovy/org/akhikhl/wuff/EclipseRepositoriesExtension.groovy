/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 *
 * @author akhikhl
 */
class EclipseRepositoriesExtension {

  protected final List repositoryList = []
  
  String defaultCategoryName
	EclipseRepositoryExtension defaultConfig

  void repository(String id = null, Closure closure) {
    if(id == null)
      id = defaultConfig?.id ?: ''
    def f = repositoryList.find { it.id == id }
    if(f == null) {
      f = new EclipseRepositoryExtension(id: id, defaultCategoryName: defaultCategoryName, defaultConfig: defaultConfig)
      repositoryList.add(f)
    }
    closure.delegate = f
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()
  }
}

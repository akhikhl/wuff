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

  protected Map repositoriesMap = [:]
  
	EclipseRepositoryExtension defaultConfig

  void repository(String id = null, Closure closure) {
    if(id == null)
      id = defaultConfig?.id ?: ''
    def f = repositoriesMap[id]
    if(f == null)
      f = repositoriesMap[id] = new EclipseRepositoryExtension(id: id, defaultConfig: defaultConfig)
    closure.delegate = f
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()
  }
}


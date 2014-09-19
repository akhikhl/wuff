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
class EclipseFeaturesExtension {

  protected Map featuresMap = [:]
  
	EclipseFeatureExtension defaultConfig

  void feature(String id = null, Closure closure) {
    if(id == null)
      id = defaultConfig?.id ?: ''
    def f = featuresMap[id]
    if(f == null)
      f = featuresMap[id] = new EclipseFeatureExtension(id: id, defaultConfig: defaultConfig)
    closure.delegate = f
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()
  }
}


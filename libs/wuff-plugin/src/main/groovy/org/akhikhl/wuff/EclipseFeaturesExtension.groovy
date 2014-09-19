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

  protected final List featureList = []
  
	EclipseFeatureExtension defaultConfig

  void feature(String id = null, Closure closure = null) {
    if(id == null)
      id = defaultConfig?.id ?: ''
    def f = featureList.find { it.id == id }
    if(f == null) {
      f = new EclipseFeatureExtension(id: id, defaultConfig: defaultConfig)
      featureList.add(f)
    }
    if(closure != null) {
      closure.delegate = f
      closure.resolveStrategy = Closure.DELEGATE_FIRST
      closure()
    }
  }
}


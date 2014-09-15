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
class EclipseFeature {

	String id
  File featureXmlFile
  
  EclipseFeature(String id, File featureXmlFile = null) {
    this.id = id
    this.featureXmlFile = featureXmlFile
  }
}


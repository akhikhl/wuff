/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 *
 * @author akhikhl
 */
class EclipseCategory {
  
	final String name
  String label
  String description
  String configuration
  
  final List features = []
  
  EclipseCategory(String name) {
    this.name = name
  }
  
  void feature(String id) {
    features.add(id)
  }
}


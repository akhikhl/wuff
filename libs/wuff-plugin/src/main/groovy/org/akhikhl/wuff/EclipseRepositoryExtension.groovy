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
class EclipseRepositoryExtension {

  String id
  String version
  String url
  String description
  final List categories = []
  String configuration
  boolean archive = true
  String archiveName

  void category(String name) {
    categories.add(new EclipseCategory(name))
  }
}

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
 * @author ahi
 */
class SwtAppPluginExtension {

  def products = []

  def product(newValue) {
    products.add newValue
  }
}


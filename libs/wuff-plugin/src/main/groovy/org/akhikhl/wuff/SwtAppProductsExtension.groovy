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
class SwtAppProductsExtension {

  boolean archiveProducts = false

  def productList = []

  def product(newValue) {
    productList.add newValue
  }
}


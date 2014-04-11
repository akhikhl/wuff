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
class RcpAppPluginExtension {

  private boolean defaultProducts = true
  def products = [[:]]

  boolean archiveProducts = false

  def launchParameters = []

  def additionalFilesToArchive = []

  def archiveFile(file) {
    additionalFilesToArchive.add file
  }

  def launchParameter(String newValue) {
    launchParameters.add newValue
  }

  def product(String productName) {
    product( [ name: productName ] )
  }

  def product(Map productSpec) {
    if(defaultProducts) {
      products = []
      defaultProducts = false
    }
    products.add productSpec
  }
}

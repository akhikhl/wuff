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
class EquinoxAppPluginExtension {

  boolean archiveProducts = false

  def beforeProductGeneration = []

  def launchParameters = []

  private boolean defaultProducts = true
  def products = [[:]]

  def additionalFilesToArchive = []

  def archiveFile(file) {
    additionalFilesToArchive.add file
  }

  def beforeProductGeneration(newValue) {
    beforeProductGeneration.add newValue
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

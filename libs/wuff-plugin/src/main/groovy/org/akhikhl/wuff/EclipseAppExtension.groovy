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
class EclipseAppExtension {

  List additionalFilesToArchive = []

  boolean archiveProducts = false

  List<Closure> beforeProductGeneration = []

  private boolean defaultProducts = true
  List products = [[:]]

  List<String> launchParameters = []

  def archiveFile(file) {
    additionalFilesToArchive.add file
  }

  void beforeProductGeneration(Closure newValue) {
    beforeProductGeneration.add newValue
  }

  void launchParameter(String newValue) {
    launchParameters.add newValue
  }

  void product(String productName) {
    product( [ name: productName ] )
  }

  void product(Map productSpec) {
    if(defaultProducts) {
      products = []
      defaultProducts = false
    }
    products.add productSpec
  }
}

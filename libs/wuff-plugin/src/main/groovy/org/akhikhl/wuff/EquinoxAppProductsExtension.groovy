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
class EquinoxAppProductsExtension {

  List additionalFilesToArchive = []

  boolean archiveProducts = false

  List<Closure> beforeProductGeneration = []

  private boolean defaultProducts = true
  List productList = [[:]]

  List<String> launchParameters = []

  List<String> jvmArgs = []

  List<String> autostartedBundles = []

  def archiveFile(file) {
    additionalFilesToArchive.add file
  }

  void beforeProductGeneration(Closure newValue) {
    beforeProductGeneration.add newValue
  }

  void launchParameter(String newValue) {
    launchParameters.add newValue
  }

  void autostartedBundle(String newBundle) {
      autostartedBundles.add newBundle
  }

  void jvmArg(String newValue) {
      jvmArgs.add newValue
  }

  void product(String productName) {
    product( [ name: productName ] )
  }

  void product(Map productSpec) {
    if(defaultProducts) {
      productList = []
      defaultProducts = false
    }
    productList.add productSpec
  }
}

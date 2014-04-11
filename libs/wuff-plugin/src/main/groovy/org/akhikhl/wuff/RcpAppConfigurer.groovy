/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class RcpAppConfigurer extends EquinoxAppConfigurer {

  private static final launchers = [ "linux" : "shell", "windows" : "windows" ]

  RcpAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void configureProducts() {

    project.rcp.products.each { product ->
      project.equinox.product(product)
    }

    project.equinox.archiveProducts = project.rcp.archiveProducts
    project.equinox.additionalFilesToArchive = project.rcp.additionalFilesToArchive
    project.equinox.launchParameters = project.rcp.launchParameters

    super.configureProducts()
  }

  @Override
  protected void createExtensions() {
    super.createExtensions()
    project.extensions.create('rcp', RcpAppPluginExtension)
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'rcpApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_rcp_'
  }
}


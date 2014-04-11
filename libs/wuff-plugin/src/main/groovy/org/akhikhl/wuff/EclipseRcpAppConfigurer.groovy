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
class EclipseRcpAppConfigurer extends EquinoxAppConfigurer {

  EclipseRcpAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected String getAppExtensionName() {
    'rcp'
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'rcpApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_rcp_'
  }
}


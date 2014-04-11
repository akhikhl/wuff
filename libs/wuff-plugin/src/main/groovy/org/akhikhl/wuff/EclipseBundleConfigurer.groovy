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
class EclipseBundleConfigurer extends OsgiBundleConfigurer {

  EclipseBundleConfigurer(Project project) {
    super(project)
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'eclipseBundle' ]
  }
}


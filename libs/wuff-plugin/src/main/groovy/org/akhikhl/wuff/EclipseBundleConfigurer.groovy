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
    super(project, 'eclipseBundle')
  }

  @Override
  protected Collection<String> getDefaultRequiredBundles() {
    [ 'org.eclipse.core.runtime', 'org.eclipse.core.resources', 'org.eclipse.ui', 'org.eclipse.jface' ]
  }
}


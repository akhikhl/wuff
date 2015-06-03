/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
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
  protected PluginXmlBuilder createPluginXmlBuilder() {
    new EclipseBundlePluginXmlBuilder(project)
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'eclipseBundle' ]
  }
}

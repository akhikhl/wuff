/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class EclipseBundlePlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    def configurer = new EclipseBundleConfigurer(project)
    configurer.apply()
  }
}


/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 *
 * @author ahi
 */
class EclipseIdeAppPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    def configurer = new EclipseIdeAppConfigurer(project)
    configurer.apply()
  }
}

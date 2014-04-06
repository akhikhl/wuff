/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class EclipseBundlePlugin implements Plugin<Project> {

  void apply(final Project project) {
    ProjectConfigurer configurer = new ProjectConfigurer(project, 'eclipseBundle')
    configurer.preConfigure()
    project.afterEvaluate {
      configurer.configure()
    }
  }
}


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
 * @author akhikhl
 */
class EclipseBundlePlugin implements Plugin<Project> {

  void apply(final Project project) {
    ProjectConfigurer configurer = new ProjectConfigurer(project, 'eclipseBundle')
    configurer.configure()
    project.afterEvaluate {
      configurer.postConfigure()
      TaskUtils.defineTask_createBundleManifest(project)
    }
  }
}


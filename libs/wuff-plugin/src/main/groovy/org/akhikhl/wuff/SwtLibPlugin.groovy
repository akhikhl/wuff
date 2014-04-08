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
class SwtLibPlugin implements Plugin<Project> {

  void apply(final Project project) {
    ProjectConfigurer configurer = new ProjectConfigurer(project, 'swtlib')
    configurer.preConfigure()
    project.afterEvaluate {
      configurer.configure()
    }
  }
}


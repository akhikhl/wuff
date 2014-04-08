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
class EclipseConfigPlugin implements Plugin<Project> {

  void apply(final Project project) {
    // configuration is created, but not applied to this project
    project.extensions.create('eclipse', EclipseConfig)
  }
}

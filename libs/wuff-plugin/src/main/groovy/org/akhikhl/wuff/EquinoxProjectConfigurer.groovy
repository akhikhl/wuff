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
class EquinoxProjectConfigurer extends ProjectConfigurer {

  EquinoxProjectConfigurer(Project project, String moduleName) {
    super(project, moduleName)
  }

  @Override
  void configure() {
    super.configure()

    project.extensions.create('run', RunExtension)
    project.extensions.create('equinox', EquinoxAppPluginExtension)

    project.task 'run', type: JavaExec
    project.task 'debug', type: JavaExec
  }
}


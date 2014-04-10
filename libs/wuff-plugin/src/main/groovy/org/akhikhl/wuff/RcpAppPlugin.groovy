/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*

/**
 *
 * @author akhikhl
 */
class RcpAppPlugin implements Plugin<Project> {

  void apply(final Project project) {
    def configurer = new RcpAppConfigurer(project)
    configurer.apply()
  }
}

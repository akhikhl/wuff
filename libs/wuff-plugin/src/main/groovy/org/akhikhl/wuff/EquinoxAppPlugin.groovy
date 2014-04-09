/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.apache.commons.io.FilenameUtils
import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*

/**
 *
 * @author akhikhl
 */
class EquinoxAppPlugin implements Plugin<Project> {

  void apply(final Project project) {
    def configurer = new EquinoxAppConfigurer(project)
    configurer.apply()
  }
}

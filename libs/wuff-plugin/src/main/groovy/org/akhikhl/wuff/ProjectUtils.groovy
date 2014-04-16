/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Project-centric utilities
 *
 * @author akhikhl
 */
final class ProjectUtils {

  static Configuration findFileInProducts(Project project, File file) {
    project.configurations.find { config ->
      config.name.startsWith('product_') && config.find { it == file }
    }
  }
}

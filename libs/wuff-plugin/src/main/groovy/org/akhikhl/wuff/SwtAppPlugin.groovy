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
class SwtAppPlugin implements Plugin<Project> {

  private static final launchers = [ 'linux' : 'shell', 'windows' : 'windows' ]

  void apply(final Project project) {

    def configurer = new ProjectConfigurer(project, 'swtapp')
    configurer.configure()

    project.apply plugin: 'onejar'
    project.extensions.create('swtapp', SwtAppPluginExtension)

    // we use onejar hook, because we need to populate onejar config
    // before onejar starts to generate products.
    project.onejar.beforeProductGeneration {

      configurer.postConfigure()

      def products = project.swtapp.products ?: [[]]

      products.each { product ->
        def platform = product.platform ?: PlatformConfig.current_os
        def arch = product.arch ?: PlatformConfig.current_arch
        def language = product.language ?: ''
        if(language)
          project.onejar.product name: "swtapp_${platform}_${arch}_${language}", launcher: launchers[platform], suffix: "${platform}-${arch}-${language}", platform: platform, arch: arch, language: language
        else
          project.onejar.product name: "swtapp_${platform}_${arch}", launcher: launchers[platform], suffix: "${platform}-${arch}", platform: platform, arch: arch
      }
    }
  }
}


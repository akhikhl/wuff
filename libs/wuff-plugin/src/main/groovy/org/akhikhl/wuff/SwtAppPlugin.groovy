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
    configurer.preConfigure()

    project.apply plugin: 'onejar'
    project.extensions.create('swtapp', SwtAppPluginExtension)

    // we use onejar hook, because we need to populate onejar config
    // before onejar starts to generate products.
    project.onejar.beforeProductGeneration {

      configurer.configure()

      PlatformConfig.supported_oses.each { platform ->
        PlatformConfig.supported_archs.each { arch ->

          configurer.apply { EclipseModuleConfig moduleConfig ->
            for(Closure closure in moduleConfig.platformSpecific)
              closure(project, platform, arch)
          }

          PlatformConfig.supported_languages.each { language ->
            configurer.apply { EclipseModuleConfig moduleConfig ->
              for(Closure closure in moduleConfig.platformAndLanguageSpecific)
                closure(project, platform, arch, language)
            }
          }
        }
      }

      def products = project.swtapp.products ?: [[]]

      products.each { product ->
        def platform = product.platform ?: PlatformConfig.current_os
        def arch = product.arch ?: PlatformConfig.current_arch
        def language = product.language ?: ''
        if(language)
          project.onejar.product name: "swt_${platform}_${arch}_${language}", launcher: launchers[platform], suffix: "${platform}-${arch}-${language}", platform: platform, arch: arch, language: language
        else
          project.onejar.product name: "swt_${platform}_${arch}", launcher: launchers[platform], suffix: "${platform}-${arch}", platform: platform, arch: arch
      }
    }
  }
}


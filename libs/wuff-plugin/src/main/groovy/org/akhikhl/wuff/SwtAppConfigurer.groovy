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
class SwtAppConfigurer extends Configurer {

  private static final launchers = [ 'linux' : 'shell', 'windows' : 'windows' ]

  SwtAppConfigurer(Project project) {
    super(project, 'swtapp')
  }

  @Override
  protected void afterEvaluate(Closure closure) {
    // we use onejar hook, because we need to populate onejar config
    // before onejar starts to generate products.
    project.onejar.beforeProductGeneration(closure)
  }

  @Override
  protected void applyPlugins() {
    super.applyPlugins()
    project.apply plugin: 'onejar'
  }

  @Override
  protected void configureProducts() {

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

  @Override
  protected void createExtensions() {
    super.createExtensions()
    project.extensions.create('swtapp', SwtAppPluginExtension)
  }
}


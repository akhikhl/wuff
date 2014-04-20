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
    super(project)
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

    def productList = project.products.productList ?: [[]]

    productList.each { product ->
      def platform = product.platform ?: PlatformConfig.current_os
      def arch = product.arch ?: PlatformConfig.current_arch
      def language = product.language ?: ''
      if(language)
        project.onejar.product name: "swtapp_${platform}_${arch}_${language}", launcher: launchers[platform], suffix: "${platform}-${arch}-${language}", platform: platform, arch: arch, language: language
      else
        project.onejar.product name: "swtapp_${platform}_${arch}", launcher: launchers[platform], suffix: "${platform}-${arch}", platform: platform, arch: arch
    }

    project.onejar.archiveProducts = project.products.archiveProducts
    project.onejar.additionalProductFiles = project.products.additionalProductFiles
    project.onejar.excludeProductFile = project.products.excludeProductFile
    project.onejar.launchParameters = project.products.launchParameters
    project.onejar.jvmMinMemory = project.products.jvmMinMemory
    project.onejar.jvmMaxMemory = project.products.jvmMaxMemory
  }

  @Override
  protected void createExtensions() {
    super.createExtensions()
    project.extensions.create('products', SwtAppProductsExtension)
  }

  @Override
  protected List<String> getModules() {
    return [ 'swtapp' ]
  }
}


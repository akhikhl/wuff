/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.akhikhl.unpuzzle.PlatformConfig

/**
 *
 * @author akhikhl
 */
class SwtAppConfigurer extends SwtLibConfigurer {

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

    def productList = project.products.productList ?: [[:]]

    productList.each { product ->
      def platform = product.platform ?: PlatformConfig.current_os
      def arch = product.arch ?: PlatformConfig.current_arch
      project.onejar.product configBaseName: 'swtapp', launcher: launchers[platform], platform: platform, arch: arch, language: product.language
    }

    project.onejar.archiveProducts = project.products.archiveProducts
    project.onejar.additionalProductFiles = project.products.additionalProductFiles
    project.onejar.excludeProductFile = project.products.excludeProductFile
    project.onejar.launchParameters = project.products.launchParameters
    project.onejar.jvmMinMemory = project.products.jvmMinMemory
    project.onejar.jvmMaxMemory = project.products.jvmMaxMemory
  }

  @Override
  protected void createConfigurations() {

    super.createConfigurations()

    PlatformConfig.supported_oses.each { platform ->
      PlatformConfig.supported_archs.each { arch ->

        String productConfigName = "product_swtapp_${platform}_${arch}"
        project.configurations.create(productConfigName)

        PlatformConfig.supported_languages.each { language ->

          String localizedProductConfigName = "product_swtapp_${platform}_${arch}_${language}"
          def localizedConfig = project.configurations.create(localizedProductConfigName)
          localizedConfig.extendsFrom project.configurations[productConfigName]
        }
      }
    }
  }

  @Override
  protected void createExtensions() {
    super.createExtensions()
    project.extensions.create('products', SwtAppProductsExtension)
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'swtapp' ]
  }
}

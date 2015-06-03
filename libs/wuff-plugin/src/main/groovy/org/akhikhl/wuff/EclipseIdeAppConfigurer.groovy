/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.nio.file.Paths
import groovy.xml.MarkupBuilder
import groovy.util.Node
import org.gradle.api.Project
import org.akhikhl.unpuzzle.PlatformConfig

/**
 *
 * @author ahi
 */
class EclipseIdeAppConfigurer extends EclipseRcpAppConfigurer {

  EclipseIdeAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void createConfigurations() {

    super.createConfigurations()

    PlatformConfig.supported_oses.each { platform ->
      PlatformConfig.supported_archs.each { arch ->

        def productConfig = project.configurations.create("product_eclipseIde_${platform}_${arch}")
        productConfig.extendsFrom project.configurations.findByName("product_rcp_${platform}_${arch}")

        PlatformConfig.supported_languages.each { language ->
          def localizedConfig = project.configurations.create("product_eclipseIde_${platform}_${arch}_${language}")
          localizedConfig.extendsFrom productConfig
          localizedConfig.extendsFrom project.configurations.findByName("product_rcp_${platform}_${arch}_${language}")
        }
      }
    }
  }

  @Override
  protected PluginXmlBuilder createPluginXmlBuilder() {
    new EclipseIdeAppPluginXmlBuilder(project)
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'eclipseIdeBundle', 'eclipseIdeApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_eclipseIde_'
  }
}

/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import groovy.xml.MarkupBuilder

/**
 *
 * @author ahi
 */
class EclipseIdeAppConfigurer extends EclipseRcpAppConfigurer {

  EclipseIdeAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void generateDefaultFiles() {
    super.generateDefaultFiles()
    if(PluginUtils.findPluginConfigFile(project) == null) {
      String xmlStr = getDefaultPluginConfig()
      ProjectUtils.stringToFile(getDefaultPluginConfig(), PluginUtils.getGeneratedPluginConfigFile(project))
    }
  }

  @Override
  protected String getAppExtensionName() {
    'eclipseIde'
  }

  private String getDefaultPluginConfig() {
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.pi eclipse: [version: '3.2']
    xml.plugin {
      extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
        product application: 'org.eclipse.ui.ide.workbench', name: project.name
      }
    }
    writer.toString()
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'eclipseIdeBundle', 'eclipseIdeApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_eclipseIde_'
  }
}


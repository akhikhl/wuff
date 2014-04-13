/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.nio.file.Paths
import groovy.xml.MarkupBuilder
import groovy.util.Node
import org.gradle.api.Project

/**
 *
 * @author ahi
 */
class EclipseIdeAppConfigurer extends EclipseRcpAppConfigurer {

  EclipseIdeAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected String getAppExtensionName() {
    'eclipseIde'
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'eclipseIdeBundle', 'eclipseIdeApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_eclipseIde_'
  }

  @Override
  protected void populatePluginXml(MarkupBuilder pluginXml, Node existingPluginXml) {
    populatePerspective(pluginXml, existingPluginXml)
    if(!existingPluginXml?.extension.find({ it.'@point' == 'org.eclipse.core.runtime.products' }))
      pluginXml.extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
        product application: 'org.eclipse.ui.ide.workbench', name: project.name
      }
  }
}

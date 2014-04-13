/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.xml.MarkupBuilder
import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class EclipseRcpAppConfigurer extends EquinoxAppConfigurer {

  EclipseRcpAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected String getAppExtensionName() {
    'rcp'
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'rcpApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_rcp_'
  }

  @Override
  protected void populateExtraPluginConfig(MarkupBuilder xml, Node existingConfig) {
    super.populateExtraPluginConfig(xml, existingConfig)
    if(!existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.perspectives' })) {
      String perspectiveClass = PluginUtils.findClassFromSource(project, '**/*Perspective.groovy', '**/*Perspective.java')
      if(perspectiveClass)
        xml.extension(point: 'org.eclipse.ui.perspectives') {
          perspective id: "${project.name}.perspective", name: project.name, 'class': perspectiveClass
        }
    }
  }
}


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
  protected String getScaffoldResourceDir() {
    'scaffold/eclipse-rcp-app/'
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'rcpApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_rcp_'
  }

  @Override
  protected void populatePluginXml(MarkupBuilder pluginXml, Node existingPluginXml) {
    super.populatePluginXml(pluginXml, existingPluginXml)
    populatePerspective(pluginXml, existingPluginXml)
  }

  protected void populatePerspective(MarkupBuilder pluginXml, Node existingConfig) {
    if(!existingConfig?.extension.find({ it.'@point' == 'org.eclipse.ui.perspectives' })) {
      String perspectiveClass = PluginUtils.findClassInSources(project, '**/*Perspective.groovy', '**/*Perspective.java')
      if(perspectiveClass)
        pluginXml.extension(point: 'org.eclipse.ui.perspectives') {
          perspective id: "${project.name}.perspective", name: project.name, 'class': perspectiveClass
        }
    }
  }

  protected void populatePluginCustomization(Properties props) {
    if(!props.containsKey('org.eclipse.ui/defaultPerspectiveId')) {
      String perspectiveId = project.pluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.perspectives' })?.perspective?.'@id'?.text()
      if(perspectiveId)
        props.setProperty('org.eclipse.ui/defaultPerspectiveId', perspectiveId)
    }
  }
}


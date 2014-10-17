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
class EclipseIdeAppPluginXmlBuilder extends EclipseRcpAppPluginXmlBuilder {

  EclipseIdeAppPluginXmlBuilder(Project project) {
    super(project)
  }

  @Override
  protected boolean mustDefineApplicationExtensionPoint() {
    false
  }

  @Override
  protected void populate(MarkupBuilder pluginXmlBuilder) {    
    populateApplications(pluginXmlBuilder)
    populateProduct(pluginXmlBuilder)
    populatePerspectives(pluginXmlBuilder)
    populateViews(pluginXmlBuilder)
    populateIntro(pluginXmlBuilder)
  }
  
  @Override
  protected void populateApplications(MarkupBuilder pluginXmlBuilder) {
    super.populateApplications(pluginXmlBuilder)
    if(applicationIds.isEmpty())
      applicationIds.add('org.eclipse.ui.ide.workbench')
  }
}


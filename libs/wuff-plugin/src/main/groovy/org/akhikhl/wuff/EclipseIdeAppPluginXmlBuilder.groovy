/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
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
  protected void populate(MarkupBuilder pluginXml) {    
    populateApplications(pluginXml)
    populateProduct(pluginXml)
    populatePerspectives(pluginXml)
    populateViews(pluginXml)
    populateIntro(pluginXml)
  }
  
  @Override
  protected void populateApplications(MarkupBuilder pluginXml) {
    super.populateApplications(pluginXml)
    if(applicationIds.isEmpty())
      applicationIds.add('org.eclipse.ui.ide.workbench')
  }
}


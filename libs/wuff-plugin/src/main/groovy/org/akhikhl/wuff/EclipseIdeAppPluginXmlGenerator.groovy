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
class EclipseIdeAppPluginXmlGenerator extends EclipseRcpAppPluginXmlGenerator {

  EclipseIdeAppPluginXmlGenerator(Project project) {
    super(project)
  }

  protected void deduceDefaultApplicationIds() {
    if(applicationIds.isEmpty())
      applicationIds.add('org.eclipse.ui.ide.workbench')
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
}


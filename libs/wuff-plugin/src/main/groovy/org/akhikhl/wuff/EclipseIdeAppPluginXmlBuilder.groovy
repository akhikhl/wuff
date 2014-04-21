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
  protected void populate(MarkupBuilder pluginXml) {
    applicationId = 'org.eclipse.ui.ide.workbench'
    populateProduct(pluginXml)
    populatePerspectives(pluginXml)
    populateViews(pluginXml)
    populateIntro(pluginXml)
  }
}


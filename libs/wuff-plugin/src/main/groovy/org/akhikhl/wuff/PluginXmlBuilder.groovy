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
class PluginXmlBuilder {

  protected final Project project
  protected final Node existingConfig

  PluginXmlBuilder(Project project) {
    this.project = project
    this.existingConfig = PluginUtils.findPluginXml(project)
  }

  String buildPluginXml() {
    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.pi eclipse: [version: '3.2']
    xml.plugin {
      existingConfig?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      populate(xml)
    }
    return writer.toString()
  }

  protected void populate(MarkupBuilder pluginXml) {
  }
}

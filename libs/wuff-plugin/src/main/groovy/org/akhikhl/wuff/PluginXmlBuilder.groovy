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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class PluginXmlBuilder {

  protected static final Logger log = LoggerFactory.getLogger(PluginXmlBuilder)

  protected final Project project
  protected final Node existingConfig

  PluginXmlBuilder(Project project) {
    this.project = project
    File pluginXmlFile = PluginUtils.findPluginXmlFile(project)
    if(pluginXmlFile) {
      if(project.wuff.filterPluginXml) {
        String pluginXmlText = pluginXmlFile.getText('UTF-8')
        Map binding = [ project: project,
          current_os: PlatformConfig.current_os,
          current_arch: PlatformConfig.current_arch,
          current_language: PlatformConfig.current_language ]
        pluginXmlText = new groovy.text.SimpleTemplateEngine().createTemplate(pluginXmlText).make(binding).toString()
        this.existingConfig = new XmlParser().parseText(pluginXmlText)
      } else
        this.existingConfig = pluginXmlFile.withReader('UTF-8') {
          new XmlParser().parse(it)
        }
    } else
      this.existingConfig = null
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

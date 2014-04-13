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
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails

/**
 *
 * @author ahi
 */
class EclipseIdeAppConfigurer extends EclipseRcpAppConfigurer {

  EclipseIdeAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    File pluginConfigFile = PluginUtils.findPluginConfigFile(project)
    if(pluginConfigFile != null)
      project.jar {
        File extraPluginConfigFile = PluginUtils.getExtraPluginConfigFile(project)
        mainSpec.eachFile { FileCopyDetails details ->
          if(details.path.endsWith('plugin.xml') && details.file != extraPluginConfigFile) {
            log.debug 'excluding {}', details.file
            log.debug 'including {}', extraPluginConfigFile
            details.exclude()
          }
        }
      }
  }

  @Override
  protected void generateExtraFiles() {
    super.generateExtraFiles()
    generateExtraPluginConfig()
  }

  private void generateExtraPluginConfig() {
    def existingConfig = PluginUtils.findPluginConfig(project)
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.pi eclipse: [version: '3.2']
    xml.plugin {
      existingConfig?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      if(!existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.core.runtime.products' }))
        extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
          product application: 'org.eclipse.ui.ide.workbench', name: project.name
        }
    }
    ProjectUtils.stringToFile(writer.toString(), PluginUtils.getExtraPluginConfigFile(project))
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
}


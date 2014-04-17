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
  protected void createExtraFiles() {
    super.createExtraFiles()
    FileUtils.stringToFile(getIntroXmlString(), PluginUtils.getExtraIntroXmlFile(project))
    for(File dir in PluginUtils.getLocalizationDirs(project))
      FileUtils.stringToFile(getIntroXmlString(dir.name), PluginUtils.getExtraIntroXmlFile(project, dir.name))
  }

  private void createIntroXml() {
    createIntroXml(null)
    for(File dir in PluginUtils.getLocalizationDirs(project))
      createIntroXml(dir.name)
  }

  private void createIntroXml(String language) {
    def existingConfig = PluginUtils.findPluginIntroXml(project, language)
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.introContent {
      existingConfig?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      populatePluginIntroXml(xml, existingConfig, language)
    }
    def introXml = new XmlParser().parseText(writer.toString())
    String key = language == null ? 'introXml' : "introXml_$language"
    project.ext[key] = (introXml.iterator() as boolean) ? introXml : null
  }

  @Override
  protected void createVirtualConfigurations() {
    super.createVirtualConfigurations()
    createIntroXml()
  }

  protected boolean extraFilesUpToDate() {
    if(!FileUtils.stringToFileUpToDate(getIntroXmlString(), PluginUtils.getExtraIntroXmlFile(project)))
      return false
    for(File dir in PluginUtils.getLocalizationDirs(project))
      if(!FileUtils.stringToFileUpToDate(getIntroXmlString(dir.name), PluginUtils.getExtraIntroXmlFile(project, dir.name)))
        return false
    return super.extraFilesUpToDate()
  }

  @Override
  protected Map getExtraFilesProperties() {
    Map result = super.getExtraFilesProperties()
    result.introXml = getIntroXmlString()
    for(File dir in PluginUtils.getLocalizationDirs(project))
      result["introXml_${dir.name}"] = getIntroXmlString(dir.name)
    return result
  }

  protected final String getIntroXmlString(String language = null) {
    String key = language == null ? 'introXml' : "introXml_$language"
    if(project.hasProperty(key) && project[key] != null) {
      def writer = new StringWriter()
      new XmlNodePrinter(new PrintWriter(writer)).print(project[key])
      return writer.toString()
    }
    return null
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'eclipseIdeBundle', 'eclipseIdeApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_eclipseIde_'
  }

  protected void populatePluginIntroXml(MarkupBuilder pluginIntroXml, Node existingPluginIntroXml, String language) {
    File introFile = PluginUtils.findPluginIntroHtmlFile(project, language)
    if(introFile) {
      String homePageId = project.pluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.intro.config' })?.config?.presentation?.'@home-page-id'.text()
      if(homePageId && !existingPluginIntroXml?.page.find { it.'@id' == homePageId })
        pluginIntroXml.page id: homePageId, url: introFile.name
    }
  }

  @Override
  protected void populatePluginXml(MarkupBuilder pluginXml, Node existingPluginXml) {
    populatePerspective(pluginXml, existingPluginXml)
    String productId
    def existingProductDef = existingPluginXml?.extension.find({ it.'@point' == 'org.eclipse.core.runtime.products' })
    if(existingProductDef)
      productId = "${project.name}.${existingProductDef.'@id'}"
    else {
      pluginXml.extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
        product application: 'org.eclipse.ui.ide.workbench', name: project.name
      }
      productId = "${project.name}.product"
    }
    File introFile = PluginUtils.findPluginIntroHtmlFile(project)
    if(introFile) {
      String introId
      def existingIntroDef = existingPluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.intro' })
      if(existingIntroDef)
        introId = existingIntroDef.intro.'@id'
      else
        introId = "${project.name}.intro"
        pluginXml.extension(point: 'org.eclipse.ui.intro') {
          intro id: introId, 'class': 'org.eclipse.ui.intro.config.CustomizableIntroPart'
          introProductBinding introId: introId, productId: productId
        }
      if(!existingPluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.intro.config' })) {
        String contentPrefix = PluginUtils.getLocalizationDirs(project) ? '$nl$/' : ''
        pluginXml.extension(point: 'org.eclipse.ui.intro.config') {
          config(id: "${project.name}.introConfigId", introId: introId, content: "${contentPrefix}intro/introContent.xml") {
            presentation('home-page-id': 'homePageId', 'standby-page-id': 'homePageId') {
              implementation kind: 'html'
            }
          }
        }
      }
    }
  }
}

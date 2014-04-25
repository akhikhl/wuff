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
class EclipseRcpAppPluginXmlBuilder extends EquinoxAppPluginXmlBuilder {

  protected String productId
  protected EclipseBundlePluginXmlBuilder eclipseBundlePluginXmlBuilder

  EclipseRcpAppPluginXmlBuilder(Project project) {
    super(project)
    eclipseBundlePluginXmlBuilder = new EclipseBundlePluginXmlBuilder(project)
  }

  @Override
  protected void populate(MarkupBuilder pluginXml) {
    populateApplication(pluginXml)
    populateProduct(pluginXml)
    populatePerspectives(pluginXml)
    populateViews(pluginXml)
    populateIntro(pluginXml)
  }

  protected void populateIntro(MarkupBuilder pluginXml) {
    File introFile = PluginUtils.findPluginIntroHtmlFile(project)
    if(introFile) {
      String introId
      def existingIntroDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.intro' })
      if(existingIntroDef)
        introId = existingIntroDef.intro?.'@id'?.text()
      else
        introId = "${project.name}.intro"
        pluginXml.extension(point: 'org.eclipse.ui.intro') {
          intro id: introId, 'class': 'org.eclipse.ui.intro.config.CustomizableIntroPart'
          introProductBinding introId: introId, productId: productId
        }
      if(!existingConfig?.extension.find({ it.'@point' == 'org.eclipse.ui.intro.config' })) {
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

  protected void populatePerspectives(MarkupBuilder pluginXml) {
    eclipseBundlePluginXmlBuilder.populatePerspectives(pluginXml)
  }

  protected void populateProduct(MarkupBuilder pluginXml) {
    def existingProductDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.core.runtime.products' })
    if(existingProductDef)
      productId = "${project.name}.${existingProductDef.'@id'}"
    else {
      pluginXml.extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
        product application: applicationId, name: project.name
      }
      productId = "${project.name}.product"
    }
  }

  protected void populateViews(MarkupBuilder pluginXml) {
    eclipseBundlePluginXmlBuilder.populateViews(pluginXml)
  }
}


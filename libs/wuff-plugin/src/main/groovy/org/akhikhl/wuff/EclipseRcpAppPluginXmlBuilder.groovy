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
  protected boolean mustDefineApplicationExtensionPoint() {
    !project.effectiveWuff.supportsE4()
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
    if(project.effectiveWuff.supportsE4() && applicationIds.isEmpty())
      applicationIds.add('org.eclipse.e4.ui.workbench.swt.E4Application')
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
    if(existingProductDef) {
      productId = existingProductDef.'@id'
      String appId = existingProductDef.product?.'@application'?.text()
      log.info 'found existing extension-point "org.eclipse.core.runtime.products", id={}, application={}', productId, appId
      productId = "${project.name}.${productId}"
    }
    else {
      if(applicationIds.isEmpty()) {
        log.error 'Error in rcp application configuration for project {}:', project.name
        log.error 'Could not generate extension-point "org.eclipse.core.runtime.products".'
        log.error 'Reason: extension-point "org.eclipse.core.runtime.applications" is undefined.'
      } else if (applicationIds.size() > 1) {
        log.error 'Error in rcp application configuration for project {}:', project.name
        log.error 'Could not generate extension-point "org.eclipse.core.runtime.products".'
        log.error 'Reason: there should be only one extension-point of type "org.eclipse.core.runtime.applications",'
        log.error 'but there were {} of them:', applicationIds.size()
        log.error '{}', applicationIds
      } else {
        String appId = applicationIds[0]
        productId = 'product'
        log.info 'generating extension-point "org.eclipse.core.runtime.products", id={}, application={}', productId, appId
        pluginXml.extension(id: productId, point: 'org.eclipse.core.runtime.products') {
          product application: appId, name: project.name, {
            if(project.effectiveWuff.supportsE4())
              property name: 'appName', value: project.name
          }
        }
        productId = "${project.name}.${productId}"
      }
    }
  }

  protected void populateViews(MarkupBuilder pluginXml) {
    eclipseBundlePluginXmlBuilder.populateViews(pluginXml)
  }
}

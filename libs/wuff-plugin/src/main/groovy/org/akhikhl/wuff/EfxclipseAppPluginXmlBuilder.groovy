package org.akhikhl.wuff

import groovy.xml.MarkupBuilder
import org.gradle.api.Project

class EfxclipseAppPluginXmlBuilder extends EquinoxAppPluginXmlBuilder {

    protected String productId

    EfxclipseAppPluginXmlBuilder(Project project) {
        super(project)
    }

    @Override
    protected void populate(MarkupBuilder pluginXml) {
        populateApplications(pluginXml)
        populateProduct(pluginXml)
    }

    @Override
    protected void populateApplications(MarkupBuilder pluginXml) {
        if(applicationIds.isEmpty())
            applicationIds.add('org.eclipse.fx.ui.workbench.fx.application')
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
                            property name: 'appName', value: project.name
                    }
                }
                productId = "${project.name}.${productId}"
            }
        }
    }

}

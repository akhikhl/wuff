/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

class EfxclipseAppConfigurer extends EquinoxAppConfigurer {

  EfxclipseAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected PluginXmlBuilder createPluginXmlBuilder() {
    new EfxclipseAppPluginXmlBuilder(project)
  }

  protected List<String> getModules() {
    super.getModules() + ['efxclipseApp']
  }

  @Override
  protected void createConfigurations() {
    super.createConfigurations()
    if (!project.configurations.findByName('osgiExtension'))
      project.configurations {
        osgiExtension
      }

  }
}


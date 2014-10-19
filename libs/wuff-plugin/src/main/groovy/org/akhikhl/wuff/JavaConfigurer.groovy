/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff
import org.gradle.api.Project
/**
 *
 * @author akhikhl
 */
class JavaConfigurer extends Configurer {

  JavaConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void applyPlugins() {
    super.applyPlugins()
    project.apply plugin: 'java'
  }

  @Override
  protected void createConfigurations() {
    super.createConfigurations()
    if(!project.configurations.findByName('provided'))
      project.configurations {
        provided
        compile.extendsFrom provided
      }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_Jar()
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTask_Jar() {
  }

  protected void createSourceSets() {
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'java' ]
  }

  @Override
  protected void postConfigure() {
    super.postConfigure()
    configureProducts()
  }

  @Override
  protected void preConfigure() {
    super.preConfigure()
    createSourceSets()
  }
}

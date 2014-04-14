/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

import org.akhikhl.unpuzzle.eclipse2maven.EclipseDownloader
import org.akhikhl.unpuzzle.eclipse2maven.EclipseDeployer
import org.akhikhl.unpuzzle.eclipse2maven.EclipseSource
import org.akhikhl.unpuzzle.osgi2maven.Deployer

/**
 *
 * @author akhikhl
 */
class EclipseMavenInstaller {

  private Project project

  EclipseMavenInstaller(Project project) {
    this.project = project
  }

  void installEclipseIntoLocalMavenRepo() {
    File localMavenRepoDir = new File(System.getProperty('user.home'), '.m2/repository')
    File eclipseRepoFolder = new File(localMavenRepoDir, project.eclipseMavenGroup)
    if(eclipseRepoFolder.exists())
      return

    List<EclipseSource> eclipseSources = []
    Binding binding = new Binding()
    binding.current_os = PlatformConfig.current_os
    binding.current_arch = PlatformConfig.current_arch
    binding.group = project.eclipseMavenGroup
    binding.source = { Map options = [:], String url ->
      def src = new EclipseSource(url: url)
      if(options.sourcesOnly)
        src.sourcesOnly = options.sourcesOnly
      if(options.languagePacksOnly)
        src.languagePacksOnly = options.languagePacksOnly
      eclipseSources.add(src)
    }
    GroovyShell shell = new GroovyShell(binding)
    EclipseMavenInstaller.class.getResourceAsStream('eclipseDownloadConfig.groovy').withReader('UTF-8') {
      shell.evaluate(it)
    }

    File downloadMarkerFile = new File(project.rootProject.buildDir, 'eclipseDownloaded')
    if(!downloadMarkerFile.exists()) {
      new EclipseDownloader().downloadAndUnpack(eclipseSources, project.rootProject.buildDir)
      downloadMarkerFile.parentFile.mkdirs()
      downloadMarkerFile.text = new java.util.Date()
    }

    Deployer mavenDeployer = new Deployer(localMavenRepoDir.toURI().toURL().toString())
    new EclipseDeployer(project.eclipseMavenGroup).deploy(eclipseSources, project.rootProject.buildDir, mavenDeployer)

    File installMarkerFile = new File(project.rootProject.buildDir, 'eclipseInstalledIntoLocalMavenRepo')
    installMarkerFile.parentFile.mkdirs()
    installMarkerFile.text = new java.util.Date()
  }
}

/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

import org.apache.commons.io.FilenameUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class LibWrapper {

  protected static final Logger log = LoggerFactory.getLogger(LibWrapper)

  private final Project project
  private final File lib
  private final WrappedLibsConfig wrappedLibsConfig
  private libManifest
  private final String baseLibName
  private String bundleName
  private String bundleVersion
  private String fragmentHost
  private String bundleFileName

  LibWrapper(Project project, File lib, WrappedLibsConfig wrappedLibsConfig) {
    this.project = project
    this.lib = lib
    this.wrappedLibsConfig = wrappedLibsConfig
    libManifest = ManifestUtils.getManifest(project, lib)
    if(!ManifestUtils.isBundle(libManifest)) {
      baseLibName = FilenameUtils.getBaseName(lib.name)
      getBundleVersionAndName()
      bundleFileName = "${bundleName}-bundle-${bundleVersion}.jar"
    }
  }

  private File generateManifestFile() {
    def m = project.osgiManifest {
      setName bundleName
      setSymbolicName bundleName
      setVersion bundleVersion
      setClassesDir lib
      setClasspath project.files(lib)
      instruction 'Bundle-ClassPath', lib.name
      instruction 'Wrapped-Library', lib.name
      if(fragmentHost)
        instruction 'Fragment-Host', fragmentHost
    }
    m = m.effectiveManifest
    Map packages = ManifestUtils.parsePackages(m.attributes['Import-Package'])
    // workarounds for dynamically referenced classes
    WrappedLibConfig wrappedLibConfig = wrappedLibsConfig.libConfigs.find { bundleName =~ it.key }?.value
    if(wrappedLibConfig) {
      log.debug 'found wrappedLibConfig for {}, excludedImports={}', bundleName, wrappedLibConfig.excludedImports
      packages = packages.findAll { key, value -> !wrappedLibConfig.excludedImports.find { key =~ it } }
    }
    m.attributes.remove 'Import-Package'
    if(packages)
      m.attributes 'Import-Package': ManifestUtils.packagesToString(packages)

    m.attributes.remove 'Class-Path'

    File manifestFile = new File(project.buildDir, "tmp/manifests/${bundleFileName}-MANIFEST.MF")
    manifestFile.parentFile.mkdirs()
    manifestFile.withWriter { m.writeTo it }
    return manifestFile
  }

  private void getBundleVersionAndName() {
    bundleVersion = libManifest?.mainAttributes?.getValue('Implementation-Version')
    if(bundleVersion) {
      def match = baseLibName =~ '(.+)-' + bundleVersion.replaceAll(/\./, /\\./)
      if(match)
        bundleName = match[0][1]
    }
    if(!bundleName) {
      bundleVersion = libManifest?.mainAttributes?.getValue('Specification-Version')
      if(bundleVersion) {
        def match = baseLibName =~ '(.+)-' + bundleVersion.replaceAll(/\./, /\\./)
        if(match)
          bundleName = match[0][1]
      }
    }
    if(!bundleName) {
      def match = baseLibName =~ /(.+)-([\d+\.]*\d+[a-zA-Z_-]*)/
      if(match) {
        bundleName = match[0][1]
        bundleVersion = match[0][2]
      }
    }
    if(bundleVersion) {
      // check for too long version numbers, replace to valid bundle version if needed
      def match = bundleVersion =~ /(\d+\.)(\d+\.)(\d+\.)(([\w-]+\.)+[\w-]+)/
      if(match)
        bundleVersion = match[0][1] + match[0][2] + match[0][3] + match[0][4].replaceAll(/\./, '-')
    }
    if(bundleVersion) {
      def match = bundleVersion =~ /([\d+\.]*\d+)([a-zA-Z_-]+)/
      if(match) {
        def suffix = match[0][2]
        if(suffix.startsWith('-'))
          suffix = suffix.substring(1)
        if(suffix) {
          if(suffix != 'patch')
            fragmentHost = bundleName
          bundleName += '-' + suffix
        }
        bundleVersion = match[0][1]
      }
    }
    bundleName = bundleName ?: baseLibName
    bundleVersion = bundleVersion ?: '1.0'
  }

  void wrap() {
    if(ManifestUtils.isBundle(libManifest))
      return
    File wrappedLibsDir = PluginUtils.getWrappedLibsDir(project)
    wrappedLibsDir.mkdirs()
    File manifestFile = generateManifestFile()
    project.ant.jar(destFile: new File(wrappedLibsDir, bundleFileName), manifest: manifestFile) {
      fileset(file: lib)
    }
    manifestFile.delete()
  }
}

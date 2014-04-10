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

/**
 *
 * @author akhikhl
 */
class LibWrapper {

  private final Project project
  private final File lib
  private libManifest
  private final String baseLibName
  private String bundleName
  private String bundleVersion
  private String fragmentHost
  private String bundleFileName

  LibWrapper(Project project, File lib) {
    this.project = project
    this.lib = lib
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
      instruction 'Bundle-Classpath', lib.name
      instruction 'Wrapped-Library', lib.name
      if(fragmentHost)
        instruction 'Fragment-Host', fragmentHost
    }
    m = m.effectiveManifest
    def packages = ManifestUtils.parsePackages(m.attributes['Import-Package'])
    // workarounds for dynamically referenced classes
    if(bundleName.startsWith('ant-optional'))
      packages.remove 'COM.ibm.netrexx.process'
    else if(bundleName.startsWith('commons-logging'))
      packages = packages.findAll { !it.key.startsWith('org.apache.log') && !it.key.startsWith('org.apache.avalon.framework.logger') }
    else if(bundleName.startsWith('avalon-framework'))
      packages = packages.findAll { !it.key.startsWith('org.apache.log') && !it.key.startsWith('org.apache.avalon.framework.parameters') }
    else if(bundleName == 'batik-js')
      packages.remove 'org.apache.xmlbeans'
    else if(bundleName == 'batik-script')
      packages.remove 'org.mozilla.javascript'
    else if(bundleName == 'fop') {
      packages.remove 'javax.media.jai'
      packages = packages.findAll { !it.key.startsWith('org.apache.tools.ant') }
    }
    else if(bundleName.startsWith('jaxb-impl')) {
      packages = packages.findAll { !it.key.startsWith('com.sun.xml.fastinfoset') }
      packages.remove 'org.jvnet.fastinfoset'
      packages.remove 'org.jvnet.staxex'
    }
    else if(bundleName == 'jdom' || bundleName == 'jdom-b8') {
      packages.remove 'oracle.xml.parser'
      packages.remove 'oracle.xml.parser.v2'
      packages.remove 'org.apache.xerces.dom'
      packages.remove 'org.apache.xerces.parsers'
      packages.remove 'org.jaxen.jdom'
      packages.remove 'org.jaxen'
    }
    else if(bundleName == 'jdom2') {
      packages.remove 'oracle.xml.parser'
      packages.remove 'oracle.xml.parser.v2'
      packages.remove 'org.apache.xerces.dom'
      packages.remove 'org.apache.xerces.parsers'
    }
    else if(bundleName.startsWith('ojdbc')) {
      packages.remove 'javax.resource'
      packages.remove 'javax.resource.spi'
      packages.remove 'javax.resource.spi.endpoint'
      packages.remove 'javax.resource.spi.security'
      packages.remove 'oracle.i18n.text.converter'
      packages.remove 'oracle.ons'
      packages.remove 'oracle.security.pki'
    }
    else if(bundleName.startsWith('saxon'))
      packages.remove 'com.saxonica.validate'
    else if(bundleName.startsWith('svnkit')) {
      packages = packages.findAll { !it.key.startsWith('org.tmatesoft.sqljet') }
      packages.remove 'org.tigris.subversion.javahl'
    }
    else if(bundleName == 'xalan')
      packages.remove 'sun.io'
    else if(bundleName == 'xmlgraphics-commons')
      packages = packages.findAll { !it.key.startsWith('com.sun.image.codec') }
    else if(bundleName == 'jaxen') {
      packages.remove 'nu.xom'
      packages = packages.findAll { !it.key.startsWith('org.jdom') && !it.key.startsWith('org.dom4j') }
    } else if(bundleName == 'xercesImpl')
      packages.remove 'sun.io'
    else if(bundleName == 'commons-jxpath')
      packages.remove 'ant-optional'
    m.attributes.remove 'Import-Package'
    if(packages)
      m.attributes(['Import-Package': ManifestUtils.packagesToString(packages)])

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

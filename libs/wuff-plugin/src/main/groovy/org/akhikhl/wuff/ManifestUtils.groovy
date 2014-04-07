/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.*
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.*

/**
 *
 * @author akhikhl
 */
class ManifestUtils {

  static java.util.jar.Manifest getManifest(Project project, File file) {
    String checksum
    file.withInputStream {
      checksum = DigestUtils.md5Hex(it)
    }
    String tmpFolder = "${project.buildDir}/tmp/manifests/${DigestUtils.md5Hex(file.absolutePath)}"
    String manifestFileName = 'META-INF/MANIFEST.MF'
    File manifestFile = new File("$tmpFolder/$manifestFileName")
    File savedChecksumFile = new File(tmpFolder, 'sourceChecksum')
    String savedChecksum = savedChecksumFile.exists() ? savedChecksumFile.text : ''
    if(savedChecksum != checksum && !manifestFile.exists()) {
      FileTree tree
      if(file.isFile() && (file.name.endsWith('.zip') || file.name.endsWith('.jar')))
        tree = project.zipTree(file)
      else if(file.isDirectory())
        tree = project.fileTree(file)
      else
        return null
      manifestFile.parentFile.mkdirs()
      manifestFile.text = ''
      project.copy {
        from tree
        include manifestFileName
        into tmpFolder
      }
      savedChecksumFile.parentFile.mkdirs()
      savedChecksumFile.text = checksum
    }
    def libManifest
    manifestFile.withInputStream {
      libManifest = new java.util.jar.Manifest(it)
    }
    return libManifest
  }

  static String getManifestEntry(java.util.jar.Manifest manifest, String entryName) {
    if(manifest != null)
      for (def key in manifest.getMainAttributes().keySet()) {
        String attrName = key.toString()
        if(attrName == entryName)
          return manifest.getMainAttributes().getValue(attrName)
      }
    return null
  }

  static boolean isBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Bundle-SymbolicName') != null || getManifestEntry(m, 'Bundle-Name') != null
  }

  static boolean isBundle(Project project, File file) {
    return isBundle(getManifest(project, file))
  }

  static boolean isFragmentBundle(Project project, File file) {
    return isFragmentBundle(getManifest(project, file))
  }

  static boolean isFragmentBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Fragment-Host') != null
  }

  static boolean isWrapperBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Wrapped-Library') != null
  }

  static String mergePackageList(String baseValue, String mergeValue) {
    Map packages
    if(baseValue) {
      packages = ManifestUtils.parsePackages(baseValue)
      if(mergeValue)
        ManifestUtils.parsePackages(mergeValue).each {
          if(it.key.startsWith('!'))
            packages.remove(it.key.substring(1))
          else
            packages[it.key] = it.value
        }
    }
    else if(mergeValue)
      packages = ManifestUtils.parsePackages(mergeValue).findAll { !it.key.startsWith('!') }
    else
      packages = [:]
    /*
     * Here we fix the problem with eclipse 4.X bundles:
     * if 'org.eclipse.xxx' are imported via 'Import-Package',
     * the application throws ClassNotFoundException.
     */
    packages = packages.findAll { !it.key.startsWith('org.eclipse') }
    return ManifestUtils.packagesToString(packages)
  }

  static String mergeRequireBundle(String baseValue, String mergeValue) {
    if(baseValue && mergeValue)
      return ((baseValue.split(',') as Set) + (mergeValue.split(',') as Set)).join(',')
    return mergeValue ?: baseValue
  }

  static String packagesToString(Map packages) {
    return packages.collect({ it.key + it.value }).join(',')
  }

  static Map parsePackages(packagesString) {
    def packages = [:]
    if(packagesString)
      packagesString.eachMatch '(\\!?[\\w\\-\\.]+)(((;[\\w\\-\\.]+((:?)=((("[^"]*")|([\\w\\-\\.]+))))?)*),?)', {
        packages[it[1]] = it[3]
      }
    return packages
  }
}

/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.*
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.*

/**
 *
 * @author akhikhl
 */
class ManifestUtils {

  public static java.util.jar.Manifest getManifest(Project project, File file) {
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

  public static String getManifestEntry(java.util.jar.Manifest manifest, String entryName) {
    if(manifest != null)
      for (def key in manifest.getMainAttributes().keySet()) {
        String attrName = key.toString()
        if(attrName == entryName)
          return manifest.getMainAttributes().getValue(attrName)
      }
    return null
  }

  public static boolean isBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Bundle-SymbolicName') != null || getManifestEntry(m, 'Bundle-Name') != null
  }

  public static boolean isBundle(Project project, File file) {
    return isBundle(getManifest(project, file))
  }

  public static boolean isFragmentBundle(Project project, File file) {
    return isFragmentBundle(getManifest(project, file))
  }

  public static boolean isFragmentBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Fragment-Host') != null
  }

  public static boolean isWrapperBundle(java.util.jar.Manifest m) {
    return getManifestEntry(m, 'Wrapped-Library') != null
  }

  public static String packagesToString(Map packages) {
    return packages.collect({ it.key + it.value }).join(',')
  }

  public static Map parsePackages(packagesString) {
    def packages = [:]
    if(packagesString)
      packagesString.eachMatch '(\\!?[\\w\\-\\.]+)(((;[\\w\\-\\.]+((:?)=((("[^"]*")|([\\w\\-\\.]+))))?)*),?)', {
        packages[it[1]] = it[3]
      }
    return packages
  }
}

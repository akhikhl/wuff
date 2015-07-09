/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.util.jar.Manifest
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.*
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.*

/**
 *
 * @author akhikhl
 */
class ManifestUtils {

  private static Map manifests = [:]
  private static manifestsLock = new Object()

  static Manifest getManifest(Project project, File file) {
    Manifest manifest
    synchronized(manifestsLock) {
      manifest = manifests[file.absolutePath]
      if(manifest == null)
        manifest = manifests[file.absolutePath] = loadManifest(project, file)
    }
    return manifest
  }

  static boolean isBundle(Manifest manifest) {
    return manifest?.mainAttributes?.getValue('Bundle-SymbolicName') != null || manifest?.mainAttributes?.getValue('Bundle-Name') != null
  }

  static boolean isBundle(Project project, File file) {
    return isBundle(getManifest(project, file))
  }

  static boolean isFragmentBundle(Project project, File file) {
    return isFragmentBundle(getManifest(project, file))
  }

  static boolean isFragmentBundle(Manifest manifest) {
    return manifest?.mainAttributes?.getValue('Fragment-Host') != null
  }

  static boolean isWrapperBundle(Manifest manifest) {
    return manifest?.mainAttributes?.getValue('Wrapped-Library') != null
  }

  private static Manifest loadManifest(Project project, File file) {
    String tmpFolder = "${project.buildDir}/tmp/manifests"
    String manifestFileName = 'META-INF/MANIFEST.MF'
    FileTree tree
    if(file.isFile() && (file.name.endsWith('.zip') || file.name.endsWith('.jar')))
      tree = project.zipTree(file)
    else if(file.isDirectory())
      tree = project.fileTree(file)
    else
      return null
    File manifestFile = new File(tmpFolder, manifestFileName)
    manifestFile.parentFile.mkdirs()
    project.copy {
      from tree
      include manifestFileName
      into tmpFolder
    }
    Manifest manifest
    manifestFile.withInputStream {
      manifest = new Manifest(it)
    }
    return manifest
  }

  static String mergeClassPath(String baseValue, String mergeValue) {
    if(baseValue && mergeValue)
      return ((baseValue.split(',') as Set) + (mergeValue.split(',') as Set)).join(',')
    return mergeValue ?: baseValue
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
    if(baseValue && mergeValue) {
      Map bundles = [:]
      List list = [baseValue, mergeValue].join(',').split(',').toList()
      list.each { bundle ->
        List bundleParams = bundle.split(';').toList()
        String name = bundleParams.get(0)
        List params = bundles.get(name) ?: []
        if (bundleParams.size() > 1)
          params.addAll(bundleParams[1..-1])
        bundles.put(name, params)
      }
      return bundles.collect { it ->
        List res = [it.key]
        res.addAll(it.value as Set)
        return res.join(';')
      }.join(',')
    }
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

/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 *
 * @author akhikhl
 */
class PlatformConfig {

  public static final supported_oses = ['windows', 'linux', 'macosx']
  public static final supported_archs = ['x86_32', 'x86_64']
  public static final supported_languages = ['de', 'fr', 'es']

  public static final String current_os

  public static final String current_arch

  public static final String current_language

  public static final Map map_os_to_suffix = [ 'windows' : 'win32.win32', 'linux' : 'gtk.linux', 'macosx' : 'cocoa.macosx' ]

  public static final Map map_os_to_filesystem_suffix = [ 'windows' : 'win32', 'linux' : 'linux', 'macosx' : 'macosx' ]

  public static final Map map_arch_to_suffix = [
    'windows-x86_32' : '.x86',
    'windows-x86_64' : '.x86_64',
    'linux-x86_32' : '.x86',
    'linux-x86_64' : '.x86_64',
    'macosx-x86_32' : '',
    'macosx-x86_64' : '.x86_64'
  ]

  public static final String current_os_suffix

  public static final String current_os_filesystem_suffix

  public static final String current_arch_suffix

  static {
    current_os = System.getProperty('os.name')
    if(current_os.substring(0, 5).equalsIgnoreCase('linux'))
      current_os = 'linux'
    else if(current_os.substring(0, 7).equalsIgnoreCase('windows'))
      current_os = 'windows'

    current_arch = System.getProperty('os.arch')
    if(current_arch == 'x86' || current_arch == 'i386')
      current_arch = 'x86_32'
    else if(current_arch == 'amd64')
      current_arch = 'x86_64'

    current_language = System.getProperty('user.language')

    current_os_suffix = map_os_to_suffix[current_os]

    current_os_filesystem_suffix = map_os_to_filesystem_suffix[current_os]

    current_arch_suffix = map_arch_to_suffix[current_os + '-' + current_arch]
  }

  static boolean isLanguageFragment(artifact) {
    artifact.name.contains '.nl_'
  }

  static boolean isPlatformFragment(artifact) {
    supported_oses.find { os ->
      supported_archs.find { arch ->
        artifact.name.endsWith map_os_to_suffix[os] + map_arch_to_suffix[os + '-' + arch]
      }
    }
  }
}

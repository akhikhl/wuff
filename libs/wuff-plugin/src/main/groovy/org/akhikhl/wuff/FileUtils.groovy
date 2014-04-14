/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.util.jar.JarFile
import org.apache.commons.codec.digest.DigestUtils

/**
 *
 * @author akhikhl
 */
class FileUtils {

  static void stringToFile(String str, File file) {
    if(str) {
      file.parentFile.mkdirs()
      file.setText(str, 'UTF-8')
    } else if(file.exists())
      file.delete()
  }

  static boolean stringToFileUpToDate(String str, File file) {
    boolean result
    if(str) {
      String fileMd5
      if(file.exists())
        file.withInputStream {
          fileMd5 = DigestUtils.md5Hex(it)
        }
      result = fileMd5 == DigestUtils.md5Hex(str)
    } else
      result = !file.exists()
    return result
  }

  void unpack(File jarFile, File destDir, Closure filter = null) {
    def jar = new JarFile(jarFile)
    jar.entries().each { entry ->
      if(filter == null || filter(entry)) {
        File destFile = new File(destDir, entry.name)
        if(entry.directory)
          destFile.mkdirs()
        else
          jar.getInputStream(entry).withStream { ins ->
            destFile.withOutputStream { outs ->
              outs << ins
            }
          }
      }
    }
  }
}


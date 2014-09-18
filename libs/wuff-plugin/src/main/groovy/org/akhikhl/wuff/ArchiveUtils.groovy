/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.akhikhl.wuff

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 *
 * @author akhikhl
 */
class ArchiveUtils {

  private static class PackToJarContext {

    protected final JarOutputStream jarStream
    protected final byte[] buffer = new byte[32 * 1024 * 1024]
    protected List fromStack = []

    PackToJarContext(JarOutputStream jarStream) {
      this.jarStream = jarStream
    }

    void add(String source) {
      add(new File(source))
    }

    void add(File source) {
      File fromDir = getEffectiveFromDir()
      if(!source.isAbsolute() && fromDir != null)
        source = new File(fromDir, source.path).canonicalFile
      String name = source.absolutePath
      if(File.separator != '/')
        name = name.replace(File.separator, '/')
      if(name.startsWith(fromDir.absolutePath)) {
        name = name.substring(fromDir.absolutePath.length())
        if(name.startsWith('/'))
          name = name.substring(1)
      }
      if (source.isDirectory()) {
        if (!name.isEmpty()) {
          if (!name.endsWith('/'))
            name += '/'
          JarEntry entry = new JarEntry(name)
          entry.setTime(source.lastModified())
          jarStream.putNextEntry(entry)
          jarStream.closeEntry()
        }
        for (File nestedFile in source.listFiles())
          add(nestedFile)
        return
      }

      JarEntry entry = new JarEntry(name)
      entry.setTime(source.lastModified())
      jarStream.putNextEntry(entry)
      source.withInputStream { ins ->
        while (true) {
          int count = ins.read(buffer)
          if (count == -1)
            break;
          jarStream.write(buffer, 0, count)
        }
      }
      jarStream.closeEntry()
    }

    void from(dir, Closure closure) {
      if(!(dir instanceof File))
        dir = new File(dir)
      if(!dir.isAbsolute() && fromStack)
        dir = new File(fromStack.last(), dir.path).canonicalFile
      fromStack.push(dir)
      try {
        closure.delegate = this
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
      } finally {
        fromStack.pop()
      }
    }

    File getEffectiveFromDir() {
      fromStack?.last()
    }
  }
	
  static void jar(Map manifestAttributes = [:], File targetFile, Closure configClosure) {
    manifestAttributes = [:] + manifestAttributes
    manifestAttributes[Attributes.Name.MANIFEST_VERSION] = '1.0'
    Manifest manifest = new Manifest()
    manifestAttributes.each { key, value ->
      manifest.mainAttributes.put(key, value)
    }
    if(targetFile.exists())
      targetFile.delete()
    targetFile.parentFile.mkdirs()
    new JarOutputStream(new FileOutputStream(targetFile), manifest).withStream { targetStream ->
      configClosure.delegate = new PackToJarContext(targetStream)
      configClosure.resolveStrategy = Closure.DELEGATE_FIRST
      configClosure()
    }
  }
}

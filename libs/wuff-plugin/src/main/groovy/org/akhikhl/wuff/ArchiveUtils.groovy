/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *
 * @author akhikhl
 */
class ArchiveUtils {

  private static class ZipPacker {

    protected final ZipOutputStream targetStream
    protected final byte[] buffer = new byte[32 * 1024 * 1024]
    protected List fromStack = []

    ZipPacker(ZipOutputStream targetStream) {
      this.targetStream = targetStream
    }

    void add(String source) {
      add(new File(source))
    }

    void add(File source) {
      File fromDir = getEffectiveFromDir()
      if(!source.isAbsolute() && fromDir != null)
        source = new File(fromDir, source.path).canonicalFile
      String name = source.absolutePath
      String dirName = fromDir.absolutePath
      if(File.separator != '/') {
        name = name.replace(File.separator, '/')
        dirName = dirName.replace(File.separator, '/')
      }
      if(name.startsWith(dirName)) {
        name = name.substring(dirName.length())
        if(name.startsWith('/'))
          name = name.substring(1)
      }
      if (source.isDirectory()) {
        if (!name.isEmpty()) {
          if (!name.endsWith('/'))
            name += '/'
          def entry = createEntry(name)
          entry.setTime(source.lastModified())
          targetStream.putNextEntry(entry)
          targetStream.closeEntry()
        }
        for (File nestedFile in source.listFiles())
          add(nestedFile)
        return
      }

      def entry = createEntry(name)
      entry.setTime(source.lastModified())
      targetStream.putNextEntry(entry)
      source.withInputStream { ins ->
        while (true) {
          int count = ins.read(buffer)
          if (count == -1)
            break;
          targetStream.write(buffer, 0, count)
        }
      }
      targetStream.closeEntry()
    }

    protected ZipEntry createEntry(String name) {
      new ZipEntry(name)
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

  private static class JarPacker extends ZipPacker {

    JarPacker(JarOutputStream targetStream) {
      super(targetStream)
    }

    @Override
    protected ZipEntry createEntry(String name) {
      new JarEntry(name)
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
      configClosure.delegate = new JarPacker(targetStream)
      configClosure.resolveStrategy = Closure.DELEGATE_FIRST
      configClosure()
    }
  }

  static void zip(File targetFile, Closure configClosure) {
    if(targetFile.exists())
      targetFile.delete()
    targetFile.parentFile.mkdirs()
    new ZipOutputStream(new FileOutputStream(targetFile)).withStream { targetStream ->
      configClosure.delegate = new ZipPacker(targetStream)
      configClosure.resolveStrategy = Closure.DELEGATE_FIRST
      configClosure()
    }
  }
}

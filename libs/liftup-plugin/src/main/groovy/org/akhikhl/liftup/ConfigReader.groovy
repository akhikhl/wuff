/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.codehaus.groovy.control.CompilerConfiguration

/**
 * Reads plugin configuration from the specified groovy script.
 * @author akhikhl
 */
class ConfigReader {

  Config readFromResource(String resourceName) {
    CompilerConfiguration cc = new CompilerConfiguration()
    cc.setScriptBaseClass(DelegatingScript.class.name)
    def classLoader = this.getClass().getClassLoader()
    GroovyShell shell = new GroovyShell(classLoader, new Binding(), cc)
    DelegatingScript script
    classLoader.getResourceAsStream(resourceName).withReader('UTF-8') {
      script = shell.parse(it)
    }
    Config config = new Config()
    script.setDelegate(config)
    script.run()
    return config
  }
}


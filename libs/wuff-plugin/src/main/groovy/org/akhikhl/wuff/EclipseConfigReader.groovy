/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.codehaus.groovy.control.CompilerConfiguration

/**
 * Reads plugin configuration from the specified groovy script.
 * @author akhikhl
 */
class EclipseConfigReader {

  EclipseConfig readFromResource(String resourceName) {
    EclipseConfig config = new EclipseConfig()
    Binding binding = new Binding()
    binding.eclipse = { Closure closure ->
      closure.resolveStrategy = Closure.DELEGATE_FIRST
      closure.delegate = config
      closure()
    }
    GroovyShell shell = new GroovyShell(binding)
    EclipseConfigReader.getClassLoader().getResourceAsStream(resourceName).withReader('UTF-8') {
      shell.evaluate(it)
    }
    return config
  }
}


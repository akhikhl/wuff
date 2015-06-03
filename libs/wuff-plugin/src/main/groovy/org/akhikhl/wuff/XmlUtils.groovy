/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.xml.MarkupBuilder

/**
 *
 * @author akhikhl
 */
class XmlUtils {

  /**
   * writes the specified node recursively to the given builder.
   * @param builder - destination xml builder
   * @param node - node to write to the builder, can be groovy.util.Node or String
   */
  static void writeNode(MarkupBuilder builder, node) {
    if(node instanceof String)
      builder.mkp.yield node
    else
      builder.invokeMethod node.name(), [ node.attributes(), {
        node.children().each {
          XmlUtils.writeNode(builder, it)
        }
      } ]
  }
}


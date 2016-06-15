package com.avaje.ebean.eclipse.internal.enhancer.builder;

import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;

import org.avaje.ebean.typequery.agent.QueryBeanTransformer;

/**
 * Perform query bean enhancement.
 */
public class QueryBeanEnhancer {

  private final QueryBeanTransformer transformer;
  
  public QueryBeanEnhancer(URL[] classPath, int enhanceDebugLevel) {
    URLClassLoader cl = new URLClassLoader(classPath);
    this.transformer = new QueryBeanTransformer("debug="+enhanceDebugLevel, cl, null);
  }
  
  /**
   * Perform query bean enhancement returning enhanced bytes or null for no enhancement.
   */
  public byte[] enhance(String className, byte[] classBytes) throws IllegalClassFormatException {
    
    return transformer.transform(null, className, null, null, classBytes);
  }
  
}

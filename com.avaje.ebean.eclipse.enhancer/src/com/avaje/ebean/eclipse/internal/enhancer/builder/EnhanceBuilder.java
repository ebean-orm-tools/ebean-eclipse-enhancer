package com.avaje.ebean.eclipse.internal.enhancer.builder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.eclipse.internal.enhancer.EnhancerPlugin;
import com.avaje.ebean.enhance.agent.MessageOutput;
import com.avaje.ebean.enhance.agent.Transformer;
import com.avaje.ebean.enhance.agent.UrlPathHelper;
import com.avaje.ebean.enhance.asm.ClassReader;
import com.avaje.ebean.enhance.asm.ClassVisitor;
import com.avaje.ebean.enhance.asm.Opcodes;

import org.avaje.ebean.typequery.agent.CombinedTransform;
import org.avaje.ebean.typequery.agent.CombinedTransform.Response;
import org.avaje.ebean.typequery.agent.QueryBeanTransformer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.launching.JavaRuntime;

public final class EnhanceBuilder extends IncrementalProjectBuilder {
  
  @Override
  protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
    
    IProject project = getProject();
    if (kind == FULL_BUILD) {
      fullBuild(monitor);
    } else {
      IResourceDelta delta = getDelta(project);
      if (delta == null) {
        fullBuild(monitor);
      } else {
        delta.accept(new DeltaVisitor(monitor));
      }
    }

    return null;
  }
  /**
   * Find the corresponding source for this class in the project.
   * @throws CoreException 
   */
  private IFile findSourcePath(IProject project, String className) throws CoreException {
    if (project.hasNature(JavaCore.NATURE_ID)) {
      IJavaProject javaProject = JavaCore.create(project);
      try {
        IType type = javaProject.findType(className);
        if (type != null) {
          IFile sourceFile = project.getWorkspace().getRoot().getFile(type.getPath());
          if (sourceFile.exists()) {
            return sourceFile;
          }
        }
      } catch (JavaModelException e) {
        EnhancerPlugin.logError("Error in findSourcePath", e);
      }
    }
    return null;
    
  }
  private void checkResource(IResource resource, IProgressMonitor monitor) throws CoreException {
    if (!((resource instanceof IFile) && resource.getName().endsWith(".class"))) {
      return;
    }

    IFile file = (IFile) resource;
    int pluginDebug = EnhancerPlugin.getDebugLevel();

    // try to place error markers on sourceFile, if it does not exist, place marker on project
    IProject project = resource.getProject();
    IFile sourceFile = null;
    
    try (InputStream is = file.getContents(); PrintStream transformLog = EnhancerPlugin.createTransformLog()) {
      byte[] classBytes = readBytes(is);
      String className = DetermineClass.getClassName(classBytes);
      sourceFile = findSourcePath(project, className);
     
      URL[] paths = getClasspath();

      if (pluginDebug >= 2) {
        EnhancerPlugin.logInfo("... processing class: " + className);
        EnhancerPlugin.logInfo("... classpath: " + Arrays.toString(paths));
      }

      int enhanceDebugLevel = EnhancerPlugin.getEnhanceDebugLevel();

      URLClassLoader cl = new URLClassLoader(paths);
      QueryBeanTransformer queryBeanTransformer = new QueryBeanTransformer("debug="+enhanceDebugLevel, cl, null);
      // TODO: This is still a big hack, QueryBeanTransformer should be refactored to use also a class like
      // "MessageOutput"
      OutputStream nullOs = new OutputStream(){
  	    public void write(int b) {
	        //NO-OP
	    }
      };
      queryBeanTransformer.setLogout(new PrintStream(nullOs) {
          @Override
		  // as far as I can see, only println is invoked in QueryBeanTransformer, so this shoud
		  // in most cases.
          public void println(String msg) {
            EnhancerPlugin.logInfo(msg);
          }
      });
      
      Transformer entityBeanTransformer = new Transformer(paths, "debug=" + enhanceDebugLevel);
      entityBeanTransformer.setLogout(new MessageOutput() {
          @Override
          public void println(String msg) {
            EnhancerPlugin.logInfo(msg);
          }
        });
      
      CombinedTransform combined = new CombinedTransform(entityBeanTransformer, queryBeanTransformer);
      Response response = combined.transform(cl, className, null, null, classBytes);
      if (response.isEnhanced()) {
        byte[] outBytes = response.getClassBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(outBytes);
        file.setContents(bais, true, false, monitor);
        if (pluginDebug >= 1) {
          EnhancerPlugin.logInfo("enhanced: " + className);
        }        
      }
      // create Markers for all errors in SourceFile
      
      if (sourceFile != null) {
        sourceFile.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);   
      }
      for(List<Throwable> list : entityBeanTransformer.getUnexpectedExceptions().values() ) {
        for (Throwable t : list) {
          createErrorMarker(sourceFile == null ? project :sourceFile, t);
        }
      }
 
    } catch (Exception e) {
      EnhancerPlugin.logError("Error during enhancement", e);
      createErrorMarker(sourceFile == null ? project :sourceFile, e);
    }
  }

  private void createErrorMarker(IResource target, Throwable t) {
    try {
      IMarker marker = target.createMarker(IMarker.PROBLEM);
      marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
      marker.setAttribute(IMarker.MESSAGE, "Error during enhancement: " + t.getMessage());
      marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
      marker.setAttribute(IMarker.LINE_NUMBER, 1);
    } catch (CoreException e) {
      EnhancerPlugin.logError("Error during creating marker", e);
    }
  }

  private void fullBuild(final IProgressMonitor monitor) {
    try {
      getProject().accept(new ResourceVisitor(monitor));
    } catch (CoreException e) {
      EnhancerPlugin.logError("Error with fullBuild", e);
    }
  }

  private URL[] getClasspath() throws CoreException {
    
    IProject project = getProject();
    IJavaProject javaProject = JavaCore.create(project);

    String[] ideClassPath = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);

    return UrlPathHelper.convertToUrl(ideClassPath);
  }

  private byte[] readBytes(InputStream in) throws IOException {
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BufferedInputStream bi = new BufferedInputStream(in);

    int len = -1;
    byte[] buf = new byte[1024];

    while ((len = bi.read(buf)) > -1) {
      baos.write(buf, 0, len);
    }

    return baos.toByteArray();
  }

  private static class DetermineClass {
    static String getClassName(byte[] classBytes) {
      ClassReader cr = new ClassReader(classBytes);
      DetermineClassVisitor cv = new DetermineClassVisitor();
      try {
        cr.accept(cv, ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);

        // should not get to here...
        throw new RuntimeException("Expected DetermineClassVisitor to throw GotClassName?");

      } catch (GotClassName e) {
        // used to skip reading the rest of the class bytes...
        return e.getClassName();
      }
    }

    private static class DetermineClassVisitor extends ClassVisitor {
      public DetermineClassVisitor() {
        super(Opcodes.ASM5);
      }

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        throw new GotClassName(name);
      }
    }

    private static class GotClassName extends RuntimeException {
      private static final long serialVersionUID = 2869058158272107957L;

      final String className;

      public GotClassName(String className) {
        super(className);
        this.className = className.replace('/', '.');
      }

      public String getClassName() {
        return className;
      }
    }

  }

  private class DeltaVisitor implements IResourceDeltaVisitor {
    private final IProgressMonitor monitor;

    private DeltaVisitor(IProgressMonitor monitor) {
      this.monitor = monitor;
    }

    @Override
    public boolean visit(IResourceDelta delta) throws CoreException {
      IResource resource = delta.getResource();
      switch (delta.getKind()) {
      case IResourceDelta.ADDED: {
        checkResource(resource, monitor);
        break;
      }
      case IResourceDelta.REMOVED: {
        break;
      }
      case IResourceDelta.CHANGED: {
        checkResource(resource, monitor);
        break;
      }
      }

      // return true to continue visiting children.
      return true;
    }
  }

  private class ResourceVisitor implements IResourceVisitor {
    private final IProgressMonitor monitor;

    private ResourceVisitor(final IProgressMonitor monitor) {
      this.monitor = monitor;
    }

    @Override
    public boolean visit(IResource resource) throws CoreException {
      checkResource(resource, monitor);
      return true;
    }
  }
}

package io.ebean.eclipse.internal.enhancer.builder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

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
//import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
//import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

import io.ebean.eclipse.internal.enhancer.EnhancerPlugin;
import io.ebean.enhance.Transformer;
import io.ebean.enhance.asm.ClassReader;
import io.ebean.enhance.asm.ClassVisitor;
import io.ebean.enhance.asm.Opcodes;
import io.ebean.enhance.common.AgentManifest;
import io.ebean.enhance.common.ClassBytesReader;
import io.ebean.enhance.common.UrlPathHelper;
import io.ebean.enhance.entity.ClassPathClassBytesReader;
import io.ebean.enhance.entity.MessageOutput;

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
  
//  /**
//   * Find the corresponding source for this class in the project.
//   */
//  private IFile findSourcePath(IProject project, String className) throws CoreException {
//    if (project.hasNature(JavaCore.NATURE_ID)) {
//      IJavaProject javaProject = JavaCore.create(project);
//      try {
//        IType type = javaProject.findType(className);
//        if (type != null) {
//          IFile sourceFile = project.getWorkspace().getRoot().getFile(type.getPath());
//          if (sourceFile.exists()) {
//            return sourceFile;
//          }
//        }
//      } catch (JavaModelException e) {
//        EnhancerPlugin.logError("Error in findSourcePath", e);
//      }
//    }
//    return null;
//  }

  private boolean isClass(IResource resource) {
    if (resource instanceof IFile) {
      IFile file = (IFile)resource;
      String extn = file.getFileExtension();
      if (extn != null && extn.equals("class")) {
        return true;
      }
    }
    return false;
  }

  private void checkResource(IResource resource, IProgressMonitor monitor) throws CoreException {
    if (!isClass(resource)) {
      return;
    }
    process(resource, monitor, transformContext());
  }
  
  private void process(IResource resource, IProgressMonitor monitor, TransformContext context) throws CoreException {

    IFile file = (IFile) resource; 
    int pluginDebug = EnhancerPlugin.getDebugLevel();

    // try to place error markers on sourceFile, if it does not exist, place marker on project
    IProject project = resource.getProject();

    byte[] classBytes;
    try (InputStream is = file.getContents()) {
      classBytes = readBytes(is);
    } catch (Exception e) {
      EnhancerPlugin.logError("Failed to read class bytes", e);
      createErrorMarker(project, e);
      return;
    }
    
    //IFile sourceFile = null;
    try {
      String className = DetermineClass.getClassName(classBytes);
      className = className.replace('.', '/');
      //sourceFile = findSourcePath(project, className);
     
      if (pluginDebug >= 2) {
        EnhancerPlugin.logInfo("... processing class: " + className);
      }

      byte[] bytes = context.transform(className, classBytes);
      
      if (bytes != null) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        file.setContents(bais, true, false, monitor);
        if (pluginDebug >= 1) {
          EnhancerPlugin.logInfo("enhanced: " + className);
        }    
      }
      
//      // create Markers for all errors in SourceFile
//      for(List<Throwable> list : context.transformer.getUnresolved()) {
//        for (Throwable t : list) {
//          createErrorMarker(sourceFile == null ? project :sourceFile, t);
//        }
//      }
 
    } catch (Exception e) {
      EnhancerPlugin.logError("Error during enhancement "+e, e);
      createErrorMarker(project, e);//sourceFile == null ? project :sourceFile, e);
    }
  }



  private void createErrorMarker(IResource target, Throwable t) {
    try {
      IMarker marker = target.createMarker(IMarker.PROBLEM);
      marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
      marker.setAttribute(IMarker.MESSAGE, "Error during enhancement: " + t);
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

  private URL[] getClasspath(IJavaProject javaProject) throws CoreException {
    
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
    private final TransformContext transformContext;

    private ResourceVisitor(final IProgressMonitor monitor) throws CoreException {
      this.monitor = monitor;
      this.transformContext = transformContext();
      transformContext.logPackages();
    }

    @Override
    public boolean visit(IResource resource) throws CoreException {
      if (isClass(resource)) {
        process(resource, monitor, transformContext);        
      }
      return true;
    }
  }
  
  /**
   * Create and return the transformation context.
   */
  private TransformContext transformContext() throws CoreException {
    
    IProject project = getProject();
    IJavaProject javaProject = JavaCore.create(project);
      
    URL[] paths = getClasspath(javaProject);

    int enhanceDebugLevel = EnhancerPlugin.getEnhanceDebugLevel();

    EnhancerPlugin.logInfo("... enhanceDebugLevel:"+enhanceDebugLevel);

    URLClassLoader classloader = new URLClassLoader(paths);
    
    ClassBytesReader reader = new ClassPathClassBytesReader(null);
    AgentManifest manifest = AgentManifest.read(classloader, null);

    // TODO: Sort out this hack to find manifest file in main/resources
    IFile ebmf = project.getFile("src/main/resources/ebean.mf");
    //IFile ebmf = findSourcePath(project, "ebean.mf");
    if (ebmf != null && ebmf.exists()) {
      EnhancerPlugin.logInfo("... found ebean manifest file");
      try {
        manifest.addResource(ebmf.getContents());
      } catch (IOException e) {
        EnhancerPlugin.logInfo("Error reading ebean.mf" + e.getMessage()); 
      }
    }
    
    Transformer transformer = new Transformer(reader, "debug=" + enhanceDebugLevel, manifest);
    transformer.setLogout(new MessageOutput() {
      
      @Override
      public void println(String arg0) {
        EnhancerPlugin.logInfo("... "+arg0);
      }
    });
    
    return new TransformContext(classloader, manifest, transformer);
  }

  class TransformContext {
    
    final Transformer transformer;
    final AgentManifest manifest;
    final URLClassLoader classloader;

    TransformContext(URLClassLoader classloader, AgentManifest manifest, Transformer transformer) {
      this.classloader = classloader;
      this.manifest = manifest;
      this.transformer = transformer;
    }

    /**
     * Perform enhancement.
     */
    public byte[] transform(String className, byte[] classBytes) throws IllegalClassFormatException {
      return transformer.transform(classloader, className, null, null, classBytes);
    }

    void logPackages() {
      EnhancerPlugin.logInfo("ebean manifest packages -" 
      + " entity: "+ manifest.getEntityPackages()
      + " transactional: "+ manifest.getTransactionalPackages()
      + " querybean: "+manifest.getQuerybeanPackages());
    }
  }
}

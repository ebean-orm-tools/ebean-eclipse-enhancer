package io.ebean.eclipse.internal.enhancer.ui;

import java.util.Iterator;

import io.ebean.eclipse.internal.enhancer.EnhancerConstants;
import io.ebean.eclipse.internal.enhancer.EnhancerPlugin;
import io.ebean.eclipse.internal.enhancer.builder.EnhanceNature;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class ToggleNatureHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    try {
      IProject project = getProject(HandlerUtil.getCurrentSelection(event), "toggle nature handler");
      if ((project != null) && project.isAccessible()) {
        EnhanceNature.toggleNature(project, project.hasNature(EnhancerConstants.NATURE_ID) ? false : true);
      }
    } catch (CoreException e) {
      throw new ExecutionException("unable to toggle enhancer nature", e);
    }

    return null;
  }

  static IProject getProject(ISelection selection, String activity) {
    if ((selection != null) && (selection instanceof IStructuredSelection)) {
      for (Iterator<?> iterator = ((IStructuredSelection) selection).iterator(); iterator.hasNext();) {
        Object object = iterator.next();
        if (object instanceof IAdaptable) {
          return (IProject) ((IAdaptable) object).getAdapter(IProject.class);
        }
      }
    }

    /*
     * it looks like there can be cases where the project can become
     * 'unselected' before this hander actually fires. a popup alert would be
     * better, but for now...
     * 
     * TODO: use popup alert to have user retry
     */
    EnhancerPlugin.logInfo("No current project selected? for activity:" + activity, null);
    return null;
  }
}

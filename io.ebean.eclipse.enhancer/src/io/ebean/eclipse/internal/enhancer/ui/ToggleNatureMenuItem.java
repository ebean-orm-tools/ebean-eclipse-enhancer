package io.ebean.eclipse.internal.enhancer.ui;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.services.IServiceLocator;

import io.ebean.eclipse.internal.enhancer.EnhancerConstants;
import io.ebean.eclipse.internal.enhancer.EnhancerPlugin;

public class ToggleNatureMenuItem extends CompoundContributionItem implements IWorkbenchContribution {
  private IServiceLocator serviceLocator;

  public ToggleNatureMenuItem() {
    super();
  }

  public ToggleNatureMenuItem(String id) {
    super(id);
  }

  @Override
  public void initialize(IServiceLocator serviceLocator) {
    this.serviceLocator = serviceLocator;
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    final IContributionItem item = new ContributionItem() {
      @Override
      public boolean isDynamic() {
        return true;
      }

      @Override
      public void fill(Menu menu, int index) {
        /*
         * i would think there should be some interface that could be
         * implemenented that would then pass in some kind of reference as to
         * what this menu was invoked against, but i'm not sure what it is, so
         * for now...
         */
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        IProject project = ToggleNatureHandler.getProject(window.getActivePage().getSelection());

        // this is the 'default' text
        String menuText = "Toggle Ebean AutoEnhancer";

        if (project != null) {
          try {
            if (project.hasNature(EnhancerConstants.NATURE_ID)) {
              menuText = "Disable Ebean Enhancer 10.x";
            } else {
              menuText = "Enable Ebean Enhancer 10.x";
            }
          } catch (CoreException e) {
            EnhancerPlugin.logError("error checking for enhancer nature", e);
            return;
          }
        }

        MenuItem item = new MenuItem(menu, SWT.CHECK, index);
        item.setText(menuText);

        item.addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent event) {
            invokeCommand();
          }
        });
      }
    };

    return new IContributionItem[] { item };
  }

  private void invokeCommand() {
    try {
      ICommandService service = (ICommandService) serviceLocator.getService(ICommandService.class);
      service.getCommand(EnhancerConstants.TOGGLE_NATURE_COMMAND_ID).executeWithChecks(new ExecutionEvent());
    } catch (ExecutionException | NotDefinedException | NotEnabledException | NotHandledException e) {
      EnhancerPlugin.logError("failed to execute command", e);
    }
  }

}

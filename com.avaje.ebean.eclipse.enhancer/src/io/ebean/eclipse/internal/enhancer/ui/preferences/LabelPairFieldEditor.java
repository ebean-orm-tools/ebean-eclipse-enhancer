package io.ebean.eclipse.internal.enhancer.ui.preferences;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * A pair of labels.
 */
public class LabelPairFieldEditor extends FieldEditor {
  Label secondLabel;

  /**
   * Create a pair of labels.
   *
   * @param labelText
   *          the left had side text
   * @param secondLabelText
   *          the right hand side text
   * @param parent
   *          the parent Composite
   */
  public LabelPairFieldEditor(String labelText, String secondLabelText, Composite parent) {
    super("label", labelText, parent);

    // secondLabel is created as part of super(...)
    secondLabel.setText(secondLabelText);
  }

  @Override
  public int getNumberOfControls() {
    return 2;
  }

  @Override
  protected void adjustForNumColumns(int numColumns) {
    GridData gd = (GridData) secondLabel.getLayoutData();
    gd.horizontalSpan = numColumns - 1;
    gd.grabExcessHorizontalSpace = gd.horizontalSpan == 1;
  }

  @Override
  protected void doFillIntoGrid(Composite parent, int numColumns) {
    getLabelControl(parent);

    secondLabel = createSecondLabelControl(parent);

    GridData gd = new GridData();
    gd.horizontalSpan = numColumns - 1;
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    secondLabel.setLayoutData(gd);
  }

  @Override
  protected void doLoad() {
    // no-op
  }

  @Override
  protected void doLoadDefault() {
    // no-op
  }

  @Override
  protected void doStore() {
    // no-op
  }

  private Label createSecondLabelControl(Composite parent) {
    secondLabel = new Label(parent, SWT.LEFT);
    secondLabel.setFont(parent.getFont());
    secondLabel.addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent event) {
        secondLabel = null;
      }
    });
    return secondLabel;
  }

}

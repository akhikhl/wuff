package myrcpapp;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.ui.part.ViewPart;

public class View extends ViewPart {

  @Override
  public void createPartControl(final Composite parent) {
    parent.setLayout(new RowLayout());
    Button btnShowDialog = new Button(parent, SWT.PUSH);
    btnShowDialog.setText("Show dialog");
    btnShowDialog.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        mybundle.HelloWorld.showMessageDialog(parent.getShell());
      }
    });
  }

  @Override
  public void setFocus() {
  }
}


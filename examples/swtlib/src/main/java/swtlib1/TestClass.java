package swtlib1;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

public final class TestClass {

  public static void showMessageDialog(Shell shell) {
    MessageDialog.openQuestion(shell, "Question", "Did you like this example program?");
  }
}

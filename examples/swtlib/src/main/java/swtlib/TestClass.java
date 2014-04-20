package swtlib;

import java.util.Locale;
import java.util.ResourceBundle;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

public final class TestClass {

  private static ResourceBundle res = ResourceBundle.getBundle(TestClass.class.getName(), Locale.getDefault());

  public static void showMessageDialog(Shell shell) {
    MessageDialog.openQuestion(shell, res.getString("DialogTitle"), res.getString("DialogMessage"));
  }
}

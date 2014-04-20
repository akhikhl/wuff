package swtlib;

import java.util.Locale;
import java.util.ResourceBundle;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

public final class TestClass {

  public static void showMessageDialog(Shell shell) {
    ResourceBundle res = ResourceBundle.getBundle(TestClass.class.getName(), Locale.getDefault());
    MessageDialog.openQuestion(shell, res.getString("DialogTitle"), res.getString("DialogMessage"));
  }
}

package equinoxappproducts;

import java.io.IOException;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import osgibundle2.TestClass;

public class MyApp implements IApplication {

  @Override
  public Object start(IApplicationContext arg0) throws Exception {
    System.out.println("Hello, application!");
    System.out.println("Testing access to org.eclipse.core.runtime.Platform: " + Platform.getLocation().toPortableString());
    System.out.println("Testing lazy activation start");
    TestClass obj = new TestClass();
    obj.doIt();
    System.out.println("Testing lazy activation finish");
    System.out.println("Application is running, press any key to finish...");
    try {
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return IApplication.EXIT_OK;
  }

  @Override
  public void stop() {
    System.out.println("Application stop requested");
  }
}

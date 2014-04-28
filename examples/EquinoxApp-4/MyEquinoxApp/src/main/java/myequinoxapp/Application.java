package myequinoxapp;

import java.io.IOException;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class Application implements IApplication {

  @Override
  public Object start(IApplicationContext ctx) throws Exception {
    mybundle.HelloWorld.sayHello();
    return IApplication.EXIT_OK;
  }

  @Override
  public void stop() {
    // From eclipse doc:
    // This method will not be called if an application exits normally from the start(IApplicationContext) method. 
  }
}

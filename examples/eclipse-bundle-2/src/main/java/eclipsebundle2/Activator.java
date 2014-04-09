package eclipsebundle2;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

  @Override
  public void start(BundleContext ctx) throws Exception {
    System.out.println(this.getClass().getName() + ".start()");
  }

  @Override
  public void stop(BundleContext arg0) throws Exception {
    System.out.println(this.getClass().getName() + ".stop()");
  }
}

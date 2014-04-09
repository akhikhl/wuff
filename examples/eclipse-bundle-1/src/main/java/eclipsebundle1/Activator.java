package eclipsebundle1;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.jxpath.JXPathContext;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

  @Override
  public void start(BundleContext arg0) throws Exception {
    System.out.println(this.getClass().getName() + ".start()");

    // here we test access to code in non-OSGi library (jxpath).
    // such access is transparent and does not require any configuration in gradle or manifest or whatsoever.
    Map<String, String> addressMap = new HashMap<String, String>();
    addressMap.put("home", "abc");
    addressMap.put("office", "def");
    JXPathContext context = JXPathContext.newContext(addressMap);
    String value = (String) context.getValue("home");
    System.out.println("Value from jxpath: " + value);
  }

  @Override
  public void stop(BundleContext arg0) throws Exception {
    System.out.println(this.getClass().getName() + ".stop()");
  }
}

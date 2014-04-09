package eclipsebundle2;

import eclipsebundle1.HelloClass;

public class TestClass {

  public void doIt() {
    // here we test access to code in another eclipse bundle
    HelloClass obj = new HelloClass();
    obj.sayHello();
  }
}

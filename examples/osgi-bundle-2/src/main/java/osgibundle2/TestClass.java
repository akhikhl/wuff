package osgibundle2;

import osgibundle1.HelloClass;

public class TestClass {

  public void doIt() {
    // here we test access to code in another osgi bundle
    HelloClass obj = new HelloClass();
    obj.sayHello();
  }
}

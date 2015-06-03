package myefxclipseapp;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import myplugin.SampleService;

public class SamplePart {

    @Inject
    private IEclipseContext context;

    @PostConstruct
    public void initialize(BorderPane borderPane) {
        Label controlLabel = new Label("This is a Java FX control " + new SampleService().getName());
        borderPane.setCenter(controlLabel);
    }
}

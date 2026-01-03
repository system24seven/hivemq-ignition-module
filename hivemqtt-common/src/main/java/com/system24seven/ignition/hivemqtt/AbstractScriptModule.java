package com.system24seven.ignition.hivemqtt;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.script.PyArgParser;
import com.inductiveautomation.ignition.common.script.builtin.KeywordArgs;
import com.inductiveautomation.ignition.common.script.hints.JythonElement;
import org.python.core.PyObject;

public abstract class AbstractScriptModule {
    static {
        /*
         This static block registers our properties bundle so that the PropertiesFileDocProvider is able
         to retrieve the documentation for our scripting functions
        */
        BundleUtil.get().addBundle(
            AbstractScriptModule.class.getSimpleName(),
            AbstractScriptModule.class.getClassLoader(),
            AbstractScriptModule.class.getName().replace('.', '/')
        );
    }

    /**
     * Make sure any 'implementation' methods you add are protected <b>or</b> throw the Jython
     * {@link org.python.core.PyIgnoreMethodTag} exception, so that they're not implicitly exposed by Ignition's
     * scripting machinery to autocomplete.
     */
    protected abstract void publishMessage(String topic, String payload, int qos);

    /**
     * An example of a "complicated" method that accepts keyword arguments.
     */
    @KeywordArgs(
            names = {"topic", "payload", "qos"},
            types = {String.class, String.class, Integer.class}
    )
    @JythonElement(docBundlePrefix = "AbstractScriptModule")
    public void publish(PyObject[] args, String[] keywords) {
        PyArgParser argParser = PyArgParser.parseArgs(
                args,
                keywords,
                new String[]{"topic", "payload", "qos"},
                new Class<?>[]{String.class, String.class, Integer.class},
                "publishMessage"
        );

        String topic = argParser.requireString("topic");
        String payload = argParser.requireString("payload");
        int qos = argParser.getInteger("qos").orElse(2);

        publishMessage(topic, payload, qos);
    }
}

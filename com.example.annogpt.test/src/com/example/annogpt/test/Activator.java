package com.example.annogpt.test;

import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.example.annogpt.test";
    private static Activator plugin;
    
    // ✨ 리스너의 유일한 인스턴스를 저장할 변수
    private static IResourceChangeListener saveListenerInstance;
    
    public Activator() {
    	plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }

    public static void setSaveListenerInstance(IResourceChangeListener listener) {
        saveListenerInstance = listener;
    }

    public static IResourceChangeListener getSaveListenerInstance() {
        return saveListenerInstance;
    }
}
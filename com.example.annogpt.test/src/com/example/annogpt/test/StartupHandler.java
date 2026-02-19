package com.example.annogpt.test;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IStartup;

public class StartupHandler implements IStartup {

    @Override
    public void earlyStartup() {
        IResourceChangeListener listener = new SaveListener();
        
        // 생성된 리스너 인스턴스를 Activator에 저장
        Activator.setSaveListenerInstance(listener);
        
        // 워크스페이스에 리스너 등록
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
            listener, IResourceChangeEvent.POST_CHANGE);

        System.out.println("✅ [AnnoGPT] Save listener registered successfully.");
    }
}
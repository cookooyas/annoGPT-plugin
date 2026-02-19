package com.example.annogpt.test.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.preference.IPreferenceStore;
import com.example.annogpt.test.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences node = DefaultScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        // 기본값 설정
        node.put(PreferenceConstants.P_MODEL_NAME, "llama3:8b");
        node.put(PreferenceConstants.P_OLLAMA_URL, "http://127.0.0.1:11434");
    }
}
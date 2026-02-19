package com.example.annogpt.test.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.ui.IWorkbench;
import com.example.annogpt.test.Activator;
import com.example.annogpt.test.util.OllamaUtil;

public class AnnoGptPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public AnnoGptPreferencePage() {
		super(GRID);
		setDescription("AnnoGPT 설정 페이지입니다.");
	}

	@Override
	public void createFieldEditors() {
		// 1. URL 입력창은 그대로 둡니다 (Ollama 주소가 바뀔 수 있으므로)
		StringFieldEditor urlEditor = new StringFieldEditor(PreferenceConstants.P_OLLAMA_URL, "Ollama API URL:",
				getFieldEditorParent());
		addField(urlEditor);

		// 2. 모델 선택창을 드롭다운(Combo)으로 변경
		String[][] modelList = OllamaUtil.getModelTags();
		ComboFieldEditor modelEditor = new ComboFieldEditor(PreferenceConstants.P_MODEL_NAME, "사용 가능한 모델 선택:",
				modelList, getFieldEditorParent());
		addField(modelEditor);
	}

	@Override
	public void init(IWorkbench workbench) {
		// init 메소드에서 저장소를 설정하는 것이 가장 안전합니다.
		if (Activator.getDefault() != null) {
			setPreferenceStore(Activator.getDefault().getPreferenceStore());
		} else {
			// Activator가 죽어도 동작하게 하는 '플랜 B'
			setPreferenceStore(new org.eclipse.ui.preferences.ScopedPreferenceStore(
					org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE, Activator.PLUGIN_ID));
		}
	}
}
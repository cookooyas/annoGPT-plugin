package com.example.annogpt.test.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.jface.preference.IPreferenceStore;
import org.json.JSONArray;
import org.json.JSONObject;

import com.example.annogpt.test.Activator;
import com.example.annogpt.test.preferences.PreferenceConstants;

public class OllamaUtil {
	public static String[][] getModelTags() {
        try {
            // 설정창에서 현재 입력된 URL을 가져옴
            IPreferenceStore store = Activator.getDefault().getPreferenceStore();
            String baseUrl = store.getString(PreferenceConstants.P_OLLAMA_URL);
            URL url = new URL(baseUrl + "/api/tags");
            
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(2000); // 응답 없으면 빨리 포기

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) content.append(line);
            
            JSONObject json = new JSONObject(content.toString());
            JSONArray models = json.getJSONArray("models");
            
            // ComboFieldEditor는 [ ["표시이름", "실제값"], ... ] 형태의 2차원 배열을 요구함
            String[][] comboData = new String[models.length()][2];
            for (int i = 0; i < models.length(); i++) {
                String name = models.getJSONObject(i).getString("name");
                comboData[i][0] = name; // 화면에 보일 이름
                comboData[i][1] = name; // 실제 저장될 값
            }
            return comboData;
        } catch (Exception e) {
            // Ollama가 꺼져있거나 연결이 안 될 경우 기본값 반환
            return new String[][] { {"연결 실패 (Ollama를 확인하세요)", "llama3:8b"} };
        }
    }
}

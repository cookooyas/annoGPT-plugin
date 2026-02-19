package com.example.annogpt.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.json.JSONArray;
import org.json.JSONObject;

import com.example.annogpt.test.preferences.PreferenceConstants;

public class GptProcessor {

	// ✨ [수정] 정규식을 단순화하여 STARTGPT 블록만 정확히 찾도록 변경
	private static final Pattern BLOCK_RE = Pattern.compile("(^\\s*)//\\s*STARTGPT([^\\n]*)\\n(.*?)^\\s*//\\s*ENDGPT",
			Pattern.MULTILINE | Pattern.DOTALL);

	// 코드 펜스(```...```)를 제거하고 그 안의 내용만 추출하는 정규식
	private static final Pattern FENCED_BLOCK_RE = Pattern.compile("```[a-zA-Z0-9_-]*\\s*\\n(.*?)\\n```",
			Pattern.DOTALL);

	// 나머지 상수 필드들은 이전과 동일
	private static final Set<String> ANALYSIS_KWS = new HashSet<>(
			Arrays.asList("구조", "구성", "의견", "평가", "분석", "리뷰", "설명", "아키텍처", "디자인", "목록", "나열", "structure",
					"architecture", "analyze", "analysis", "review", "describe", "list", "explain", "thoughts"));
	private static final Set<String> CODE_VERBS = new HashSet<>(Arrays.asList("코드", "구현", "함수", "클래스", "작성", "테스트",
			"리팩터", "리팩토링", "code", "implement", "write", "function", "class", "test", "refactor", "build"));
	private static final String SYSTEM_PROMPT_COMMENTS = "You are a technical reviewer and documentation writer for Java.\n\nOUTPUT POLICY\n- Return a SINGLE fenced code block (```), with no text before or after.\n- The code block must contain ONLY Java comments (using // or /* */).\n- Every non-empty line MUST start with a comment prefix.\n- Do NOT include imports, function/class definitions, or any runnable statements.\n- Prefer Korean for explanations; do not translate code identifiers or API names.";
	private static final String SYSTEM_PROMPT_CODE = 
		    "You are a professional Java Architect.\n\n" +
		    "TASK:\n" +
		    "- Your goal is to provide the FULL source code of a SINGLE Java file.\n" +
		    "- Preserve existing logic unless asked to change it.\n" +
		    "- Include the correct 'package' declaration and all necessary 'imports'.\n" +
		    "- The result must be a complete, runnable Java source file.\n\n" +
		    "OUTPUT POLICY:\n" +
		    "- Return ONLY a SINGLE fenced code block (```java ... ```).\n" +
		    "- No conversational text before or after the code block.";
	
	public String processFileContent(IFile file, String originalContent) {
		Matcher matcher = BLOCK_RE.matcher(originalContent);
		StringBuffer sb = new StringBuffer();

		IPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "com.example.annogpt.test");
		String defaultModel = store.getString(PreferenceConstants.P_MODEL_NAME);

		while (matcher.find()) {
			// ✨ [수정] 기존 블록을 확인하는 로직을 완전히 제거

			String prefix = matcher.group(1);
			String optline = matcher.group(2).trim();
			String body = matcher.group(3).trim();
			String model = defaultModel;
			String contextMode = "auto";
			JSONObject opts = null;
			try {
				if (optline.startsWith("{") && optline.endsWith("}")) {
					opts = new JSONObject(optline);
					model = opts.optString("model", model);
					contextMode = opts.optString("ctx", "auto");
				}
			} catch (Exception e) {
				System.err.println("⚠️ [AnnoGPT] Could not parse options: " + optline);
			}

			String gptResponse;
			try {
				String outputMode = decideOutputMode(body, opts);
				String systemPrompt = "comments".equals(outputMode) ? SYSTEM_PROMPT_COMMENTS : SYSTEM_PROMPT_CODE;
				System.out.println("➡️ [AnnoGPT] Calling Ollama API... (Context: " + contextMode + ", Output: "
						+ outputMode + ")");

				String userPrompt = buildUserPrompt(file, originalContent, matcher, prefix, body, contextMode);

				System.out.println("✅ [AnnoGPT] Final user prompt size: " + userPrompt.length() + " characters.");
				System.out.println("✅ [AnnoGPT] Final user prompt content: \n" + userPrompt);
				gptResponse = callOllamaApi(model, systemPrompt, userPrompt);
				System.out.println("✅ [AnnoGPT] Received response from Ollama.");

			} catch (Exception e) {
				e.printStackTrace();
				gptResponse = "/*\n[AnnoGPT] ERROR: GPT API call failed.\n" + e.getMessage() + "\n*/";
			}

			// ✨ [핵심 수정] 교체할 내용을 '영수증 주석 + 생성된 코드'로 구성
			String extractedPayload = extractCodePayload(gptResponse);
			String indentedPayload = indentText(extractedPayload, prefix);

			String receiptComment = buildReceiptComment(prefix, model, contextMode, body);

			// STARTGPT 블록 전체를 영수증과 생성된 코드로 완전히 대체
			String replacement = receiptComment + indentedPayload;

			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}
	
	public String processFileContent2(IFile file, String originalContent) {
	    Matcher matcher = BLOCK_RE.matcher(originalContent);
	    
	    // 블록이 없으면 처리 안 함
	    if (!matcher.find()) return originalContent;

	    // 설정값 로드
	    IPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "com.example.annogpt.test");
	    String model = store.getString(PreferenceConstants.P_MODEL_NAME);
	    if (model.isEmpty()) model = "llama3:8b";

	    String promptBody = matcher.group(3).trim();
	    String gptResponse;

	    try {
	        // AI에게 현재 파일 전체 내용을 문맥으로 주고, 전체 소스 수정을 요청함
	        String userPrompt = "--- CURRENT FILE CONTENT ---\n" + originalContent + 
	                            "\n\n--- INSTRUCTION ---\n" +
	                            "Modify or rewrite the file based on this task: " + promptBody + "\n" +
	                            "Important: Provide the FULL modified code including package and imports.";
	        System.out.println("➡️ [AnnoGPT] Requesting full file replacement...");
			System.out.println("✅ [AnnoGPT] Final user prompt size: " + userPrompt.length() + " characters.");
			System.out.println("✅ [AnnoGPT] Final user prompt content: \n" + userPrompt);
	        gptResponse = callOllamaApi(model, SYSTEM_PROMPT_CODE, userPrompt);
			System.out.println("✅ [AnnoGPT] Received response from Ollama.");
	        
	    } catch (Exception e) {
	        e.printStackTrace();
	        return originalContent; // 에러 시 원본 보존
	    }

	    String fullCode = extractCodePayload(gptResponse);
	    
	    // ✨ 상단에 영수증 주석 추가
	    String receipt = buildReceiptComment("", model, "full-file", promptBody);
	    
	    return receipt + "\n" + fullCode;
	}

	// ✨ [신규] 프롬프트 생성을 별도 메소드로 분리하여 가독성 향상
	private String buildUserPrompt(IFile file, String originalContent, Matcher matcher, String prefix, String body,
			String contextMode) {
		if ("none".equalsIgnoreCase(contextMode)) {
			return body;
		}

		IJavaProject javaProject = JavaCore.create(file.getProject());
		String jdkVersion = javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true);

		String codeBefore = originalContent.substring(0, matcher.start());
		String codeAfter = originalContent.substring(matcher.end());
		String placeholder = prefix + "// [ YOUR CODE GOES HERE ]\n";

		int beforeStart = Math.max(0, codeBefore.length() - 2000);
		int afterEnd = Math.min(codeAfter.length(), 2000);

		String codeSkeleton = "... (omitted code) ...\n" + codeBefore.substring(beforeStart) + placeholder
				+ codeAfter.substring(0, afterEnd) + "\n... (omitted code) ...";
		String projectContext = new JdtContextBuilder().buildContextFor(file);

		return "Please follow the instructions below.\n\n" + "--- CODE SKELETON ---\n"
				+ "Your generated code will replace the placeholder '[ YOUR CODE GOES HERE ]' in the following code skeleton.\n"
				+ "```java\n" + codeSkeleton + "\n```\n\n" + "--- FULL PROJECT CONTEXT ---\n"
				+ "The following directory structure and code summaries exist in the project for your reference.\n\n"
				+ projectContext + "\n--- IMPORTANT INSTRUCTION ---\n"
				+ "DO NOT repeat or write any of the code from the PROJECT CONTEXT section. Your response must ONLY contain the new code snippet for the placeholder.\n\n"
				+ "--- ENVIRONMENT ---\n" + "You must write code that is compatible with Java version " + jdkVersion
				+ ".\n\n" + "--- TASK ---\n" + body;
	}

	private String buildReceiptComment(String prefix, String model, String contextMode, String body) {
		StringBuilder receipt = new StringBuilder();
		receipt.append(prefix).append("/*\n");
		receipt.append(prefix).append(" * This source was generated by AnnoGPT.\n");
		receipt.append(prefix).append(" * ----------------------------------------\n");
		receipt.append(prefix).append(" * model: ").append(model).append("\n");
		receipt.append(prefix).append(" * ctx: ").append(contextMode).append("\n");
		receipt.append(prefix).append(" * ----------------------------------------\n");
		receipt.append(prefix).append(" * prompt:\n");

		// 프롬프트의 각 줄을 들여쓰기하여 추가
		for (String line : body.split("\n")) {
			receipt.append(prefix).append(" * ").append(line.trim()).append("\n");
		}
		receipt.append(prefix).append(" */\n");

		return receipt.toString();
	}

	private String decideOutputMode(String prompt, JSONObject opts) {
		if (opts != null && opts.has("output")) {
			String mode = opts.getString("output").toLowerCase();
			if ("code".equals(mode) || "comments".equals(mode)) {
				return mode;
			}
		}
		String p = prompt.toLowerCase();
		boolean hasAnalysis = ANALYSIS_KWS.stream().anyMatch(p::contains);
		boolean hasCodeVerb = CODE_VERBS.stream().anyMatch(p::contains);
		return hasAnalysis && !hasCodeVerb ? "comments" : "code";
	}

	private String callOllamaApi(String model, String systemPrompt, String userPrompt) throws Exception {
		IPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "com.example.annogpt.test");
		String baseUrl = store.getString(PreferenceConstants.P_OLLAMA_URL);
		String baseFullUrl = baseUrl.endsWith("/") ? baseUrl + "api/chat" : baseUrl + "/api/chat";

		URL url = new URL(baseFullUrl);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);
		con.setConnectTimeout(5000);
		con.setReadTimeout(180000);

		JSONObject systemMessage = new JSONObject();
		systemMessage.put("role", "system");
		systemMessage.put("content", systemPrompt);
		JSONObject userMessage = new JSONObject();
		userMessage.put("role", "user");
		userMessage.put("content", userPrompt);
		JSONArray messages = new JSONArray();
		messages.put(systemMessage);
		messages.put(userMessage);
		JSONObject jsonInput = new JSONObject();
		jsonInput.put("model", model);
		jsonInput.put("messages", messages);
		jsonInput.put("stream", false);

		try (OutputStream os = con.getOutputStream()) {
			byte[] input = jsonInput.toString().getBytes(StandardCharsets.UTF_8);
			os.write(input, 0, input.length);
		}

		StringBuilder response = new StringBuilder();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
			String responseLine;
			while ((responseLine = br.readLine()) != null) {
				response.append(responseLine.trim());
			}
		}

		JSONObject jsonResponse = new JSONObject(response.toString());
		return jsonResponse.getJSONObject("message").getString("content");
	}

	private String extractCodePayload(String text) {
		Matcher m = FENCED_BLOCK_RE.matcher(text);
		if (m.find()) {
			return m.group(1).trim();
		}
		return text.trim().replace("```java", "").replace("```", "").trim();
	}

	private String indentText(String text, String prefix) {
		if (text == null || text.isEmpty() || prefix == null || prefix.isEmpty()) {
			return text;
		}
		String[] lines = text.split("\n");
		StringBuilder indentedText = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (!lines[i].trim().isEmpty()) {
				indentedText.append(prefix);
			}
			indentedText.append(lines[i]);
			if (i < lines.length - 1) {
				indentedText.append("\n");
			}
		}
		return indentedText.toString();
	}
}
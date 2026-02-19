package com.example.annogpt.test.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.HandlerUtil;

import com.example.annogpt.test.ToggleState;

public class ToggleHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			// 1. UI의 체크 상태를 반전시키고 새로운 상태를 가져옴
			boolean newState = !HandlerUtil.toggleCommandState(event.getCommand());

			// 2. 실제 로직용 ToggleState에 반영
			com.example.annogpt.test.ToggleState.getInstance().setEnabled(newState);

			System.out.println("🚀 [AnnoGPT] 토글 상태 변경: " + (newState ? "ON" : "OFF"));
			
			ICommandService commandService = HandlerUtil.getActiveWorkbenchWindow(event).getService(ICommandService.class);
			commandService.refreshElements(event.getCommand().getId(), null);
		} catch (ExecutionException e) {
			System.err.println("❌ 토글 상태를 찾을 수 없습니다. plugin.xml의 <state> 설정을 확인하세요.");
			throw e;
		}
		return null;
	}
}
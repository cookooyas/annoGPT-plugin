package com.example.annogpt.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map; // 👈 import 추가
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // 👈 import 추가
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class SaveListener implements IResourceChangeListener {

	// ✨ [핵심 수정 1] 현재 처리 중인 파일을 추적하는 스레드 안전한 Map (잠금 장치)
	private static final Map<IFile, Boolean> currentlyProcessing = new ConcurrentHashMap<>();

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if (!ToggleState.getInstance().isEnabled()) {
			return;
		}
		final Set<IFile> processedInEvent = new HashSet<>();
		try {
			event.getDelta().accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(IResourceDelta delta) throws CoreException {
					if (delta.getKind() != IResourceDelta.CHANGED)
						return true;

					IResource resource = delta.getResource();
					if (resource.getType() == IResource.FILE && "java".equalsIgnoreCase(resource.getFileExtension())) {
						IFile file = (IFile) resource;
						if (processedInEvent.contains(file))
							return true;

						processedInEvent.add(file);
						processFile(file);
					}
					return true;
				}
			});
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void processFile(IFile file) {
		// ✨ [핵심 수정 2] 파일이 이미 처리 중인지 확인하고, 그렇다면 새 Job을 시작하지 않음
		if (currentlyProcessing.putIfAbsent(file, Boolean.TRUE) != null) {
			System.out.println(
					"⚠️ [AnnoGPT] Job for " + file.getName() + " is already running. Skipping duplicate request.");
			return;
		}

		Job updateJob = new Job("AnnoGPT File Modifier") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					System.out.println("✅ [AnnoGPT] Job started for: " + file.getName());
					Thread.sleep(200);

					InputStream inputStream = file.getContents();
					String originalContent = new BufferedReader(
							new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()
									.collect(Collectors.joining("\n"));
					inputStream.close();

					GptProcessor processor = new GptProcessor();
					String newContent = processor.processFileContent2(file, originalContent);

					if (!originalContent.equals(newContent)) {
						System.out.println("🎉 [AnnoGPT] Content has changed. Updating file: " + file.getName());

						IResourceChangeListener listener = Activator.getSaveListenerInstance();
						try {
							if (listener != null) {
								ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
							}

							InputStream newInputStream = new ByteArrayInputStream(
									newContent.getBytes(StandardCharsets.UTF_8));
							file.setContents(newInputStream, IFile.FORCE, monitor);
							newInputStream.close();

						} finally {
							if (listener != null) {
								ResourcesPlugin.getWorkspace().addResourceChangeListener(listener,
										IResourceChangeEvent.POST_CHANGE);
							}
						}
					} else {
						System.out.println("ℹ️ [AnnoGPT] No changes needed for: " + file.getName());
					}
				} catch (Exception e) {
					System.err.println("❌ [AnnoGPT] Error during file processing job.");
					e.printStackTrace();
					return new Status(IStatus.ERROR, "com.example.annogpt.test", "Error updating file", e);
				} finally {
					// ✨ [핵심 수정 3] Job이 끝나면 성공/실패 여부와 관계없이 잠금을 해제
					currentlyProcessing.remove(file);
				}
				return Status.OK_STATUS;
			}
		};
		updateJob.schedule();
	}
}
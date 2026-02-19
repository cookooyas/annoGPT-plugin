package com.example.annogpt.test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

public class ProjectContextCache {

    private static final ProjectContextCache INSTANCE = new ProjectContextCache();
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private boolean isPopulated = false;

    private ProjectContextCache() {}

    public static ProjectContextCache getInstance() {
        return INSTANCE;
    }

    public String get(String path) {
        return cache.get(path);
    }

    public void put(String path, String summary) {
        cache.put(path, summary);
    }
    
    public Map<String, String> getAll() {
        return new ConcurrentHashMap<>(cache);
    }

    public boolean isPopulated() {
        return isPopulated;
    }

    /**
     * 프로젝트의 모든 소스 파일을 스캔하여 캐시를 채웁니다. (느린 작업)
     */
    public synchronized void populate(IJavaProject javaProject) {
        if (isPopulated) return;
        
        System.out.println("🚀 [AnnoGPT] Starting initial project scan for context caching...");
        long startTime = System.currentTimeMillis();
        
        try {
            for (IPackageFragment pkg : javaProject.getPackageFragments()) {
                if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    for (ICompilationUnit cu : pkg.getCompilationUnits()) {
                        String path = cu.getResource().getFullPath().toString();
                        
                        // ✨ [핵심 수정] MethodVisitor 객체를 받은 후, .getFileStructure()를 호출하여 String으로 변환
                        MethodVisitor visitor = JdtContextBuilder.parseStructure(cu);
                        if (visitor != null) {
                            String summary = visitor.getFileStructure();
                            cache.put(path, summary);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        isPopulated = true;
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ [AnnoGPT] Project scan complete. (" + duration + " ms)");
    }
}
package com.example.annogpt.test;

import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class JdtContextBuilder {

    public String buildContextFor(IFile currentFile) {
        IJavaProject javaProject = JavaCore.create(currentFile.getProject());
        ProjectContextCache cache = ProjectContextCache.getInstance();

        if (!cache.isPopulated()) {
            cache.populate(javaProject);
        }

        String currentFilePath = currentFile.getFullPath().toString();
        ICompilationUnit currentCu = JavaCore.createCompilationUnitFrom(currentFile);
        
        MethodVisitor visitor = parseStructure(currentCu);
        if (visitor != null) {
            cache.put(currentFilePath, visitor.getFileStructure());
        }

        StringBuilder contextBlob = new StringBuilder();
        
        // ✨ [추가] 1. 디렉토리 구조 추가
        contextBlob.append("## Directory Tree\n");
        contextBlob.append(buildDirectoryTree(currentFile.getProject()));
        contextBlob.append("\n---\n");
        
        contextBlob.append("## Project Code Summary\n");
        
        Map<String, String> allSummaries = cache.getAll();
        
        // 2. 현재 파일을 최우선으로 추가
        String currentFileSummary = allSummaries.get(currentFilePath);
        if (currentFileSummary != null) {
            contextBlob.append("### File: ").append(currentFilePath).append(" (Currently being edited)\n");
            contextBlob.append("```java\n").append(currentFileSummary).append("\n```\n");
        }

        // 3. 나머지 모든 파일을 추가
        for (Map.Entry<String, String> entry : allSummaries.entrySet()) {
            if (entry.getKey().equals(currentFilePath)) continue;
            
            contextBlob.append("### File: ").append(entry.getKey()).append("\n");
            contextBlob.append("```java\n").append(entry.getValue()).append("\n```\n");
        }

        return contextBlob.toString();
    }
    
    // ✨ [신규] 디렉토리 구조를 문자열로 만드는 메소드
    private String buildDirectoryTree(IProject project) {
        StringBuilder tree = new StringBuilder();
        try {
            appendTree(tree, project, "");
        } catch (CoreException e) {
            e.printStackTrace();
            return "Could not build directory tree.";
        }
        return tree.toString();
    }

    private void appendTree(StringBuilder tree, IResource resource, String prefix) throws CoreException {
        // 무시할 디렉토리 목록
        if (resource.getName().equals("bin") || resource.getName().equals(".settings") || resource.getName().equals(".git")) {
            return;
        }
        tree.append(prefix).append("- ").append(resource.getName()).append("\n");
        if (resource instanceof IFolder) {
            for (IResource member : ((IFolder) resource).members()) {
                appendTree(tree, member, prefix + "  ");
            }
        } else if (resource instanceof IProject) {
             for (IResource member : ((IProject) resource).members()) {
                appendTree(tree, member, prefix + "  ");
            }
        }
    }

    public static MethodVisitor parseStructure(ICompilationUnit cu) {
        if (cu == null) return null;
        try {
            String source = cu.getSource();
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            parser.setStatementsRecovery(true);
            
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
            if (astRoot == null) return null;
            
            return new MethodVisitor(astRoot, source);
        } catch (JavaModelException e) {
            e.printStackTrace();
            return null;
        }
    }
}
package com.example.annogpt.test;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class MethodVisitor extends ASTVisitor {
    private final CompilationUnit astRoot;
    private final String sourceCode;
    
    // ✨ [수정] 메서드 시그니처 대신, 실제 코드 내용을 담을 리스트
    private final List<String> allMethodsContent = new ArrayList<>();
    private String topLevelClassName = null; // 파일의 최상위 클래스 이름
    
    // 삽입 위치 정보를 저장하는 필드는 그대로 유지
    private String enclosingClassName = null;
    private String enclosingMethodSignature = null;
    
    // 너무 긴 메서드를 잘라내기 위한 상수
    private static final int MAX_METHOD_CHARS = 1500;

    public MethodVisitor(CompilationUnit astRoot, String sourceCode) {
        this.astRoot = astRoot;
        this.sourceCode = sourceCode;
        // 1. 먼저 삽입 위치를 찾습니다.
        findEnclosingNodes();
        // 2. 그 다음 파일 전체를 방문하여 구조를 요약합니다.
        astRoot.accept(this);
    }

    // ✨ [핵심 수정] 메서드 방문 시, 시그니처 대신 전체 코드 내용을 추출
    @Override
    public boolean visit(MethodDeclaration node) {
        int start = node.getStartPosition();
        int length = node.getLength();
        String methodText = sourceCode.substring(start, start + length);
        
        // 너무 긴 메서드는 잘라냅니다.
        if (methodText.length() > MAX_METHOD_CHARS) {
            methodText = methodText.substring(0, MAX_METHOD_CHARS) + "\n... (method truncated)\n}";
        }
        allMethodsContent.add(methodText);
        return false; // 메서드 내부는 더 이상 방문하지 않습니다.
    }
    
    // ✨ [핵심 수정] 타입(클래스) 방문 시, 이름을 저장하고 자식 노드(메서드)를 계속 방문하도록 함
    @Override
    public boolean visit(TypeDeclaration node) {
        // 최상위 클래스 이름만 저장 (중첩 클래스 무시)
        if (topLevelClassName == null) {
            this.topLevelClassName = node.getName().getIdentifier();
        }
        return true; // 자식 노드(메서드 등)를 계속 방문하도록 true 반환
    }

    // ✨ [핵심 수정] 파일 구조를 포맷팅하는 로직을 업그레이드
    public String getFileStructure() {
        StringBuilder sb = new StringBuilder();
        if (topLevelClassName != null) {
            // 클래스 선언부를 간단히 표현
            sb.append("public class ").append(topLevelClassName).append(" {\n\n");
        }
        
        for (String methodBody : allMethodsContent) {
            // 가독성을 위해 각 메서드를 들여쓰기하여 추가
            for (String line : methodBody.split("\n")) {
                sb.append("    ").append(line).append("\n");
            }
            sb.append("\n"); // 메서드 간 간격 추가
        }

        if (topLevelClassName != null) {
            sb.append("}\n");
        }
        return sb.toString();
    }
    
    // =====================================================================
    // 아래의 '삽입 위치 분석' 관련 코드는 기존 로직을 그대로 유지합니다.
    // =====================================================================

    public String getEnclosingInfo() {
        if (enclosingClassName == null) {
            return "Code will be inserted in the top-level of the file.\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Code will be inserted at the following location:\n");
        sb.append("- Class: ").append(enclosingClassName).append("\n");
        if (enclosingMethodSignature != null) {
            sb.append("- Method: ").append(enclosingMethodSignature).append("\n");
        } else {
            sb.append("- Location: Inside the class body, outside of any method.\n");
        }
        return sb.toString();
    }
    
    @SuppressWarnings("unchecked")
    private void findEnclosingNodes() {
        int gptBlockPosition = -1;
        for (Comment comment : (List<Comment>) astRoot.getCommentList()) {
            int start = comment.getStartPosition();
            String commentText = this.sourceCode.substring(start, start + comment.getLength());
            if (commentText.contains("STARTGPT")) {
                gptBlockPosition = start;
                break;
            }
        }
        
        if (gptBlockPosition == -1) return;

        final int finalPos = gptBlockPosition;
        astRoot.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                if (contains(node.getStartPosition(), node.getLength(), finalPos)) {
                    enclosingClassName = node.getName().getIdentifier();
                }
                return true;
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                if (contains(node.getStartPosition(), node.getLength(), finalPos)) {
                    enclosingMethodSignature = buildSignature(node);
                }
                return true;
            }
            
            private boolean contains(int start, int length, int pos) {
                return start <= pos && pos < (start + length);
            }
        });
    }
    
    private String buildSignature(MethodDeclaration node) {
        StringBuilder signature = new StringBuilder();
        node.modifiers().forEach(mod -> signature.append(mod.toString()).append(" "));
        if (node.getReturnType2() != null) {
            signature.append(node.getReturnType2().toString()).append(" ");
        }
        signature.append(node.getName().getIdentifier());
        signature.append("(");
        List<String> params = new ArrayList<>();
        for (Object param : node.parameters()) {
            if (param instanceof SingleVariableDeclaration) {
                SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
                params.add(svd.getType().toString() + " " + svd.getName().getIdentifier());
            }
        }
        signature.append(String.join(", ", params));
        signature.append(")");
        return signature.toString();
    }
}
package com.github.bpazy.eql.intention;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author ziyuan
 * created on 2017/11/7
 */
public class JumpToJavaIntention extends BaseIntentionAction {

    private Pattern pattern = Pattern.compile("--\\s*\\[(.+)]");

    @NotNull
    @Override
    public String getText() {
        return "Jump to Java";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "eql";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        int startOffset = editor.getCaretModel().getVisualLineStart();
        int endOffset = editor.getCaretModel().getVisualLineEnd();

        String text = editor.getDocument().getText(new TextRange(startOffset, endOffset));
        if (StringUtils.isEmpty(text)) return false;

        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        String javaFileName = file.getName().replace(".eql", ".java");

        int startOffset = editor.getCaretModel().getVisualLineStart();
        int endOffset = editor.getCaretModel().getVisualLineEnd();
        String text = editor.getDocument().getText(new TextRange(startOffset, endOffset));

        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return;

        String methodName = matcher.group(1);
        if (StringUtils.isEmpty(methodName)) return;

        String path = file.getVirtualFile().getPath();
        String[] split = path.split("resources/");
        if (split.length < 2) {
            return;
        }
        String eqlFilePackageName = split[1]
                .substring(0, split[1].lastIndexOf('/'))
                .replaceAll("/", ".");

        PsiFile[] files = PsiShortNamesCache.getInstance(project).getFilesByName(javaFileName);
        for (PsiFile file1 : files) {
            PsiElement methodElement = seekJavaMethod(eqlFilePackageName, file1, methodName);
            if (methodElement == null) continue;

            Document document = PsiDocumentManager.getInstance(project).getDocument(file1);
            if (document == null) continue;

            int textOffset = methodElement.getTextOffset();
            int lineNumber = document.getLineNumber(textOffset);

            // 打开对应java文件
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file1.getVirtualFile());
            Editor eqlEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
            if (eqlEditor == null) return;

            // 跳转到java文件中函数的位置
            CaretModel caretModel = eqlEditor.getCaretModel();
            LogicalPosition logicalPosition = caretModel.getLogicalPosition();
            logicalPosition.leanForward(true);
            LogicalPosition logical = new LogicalPosition(lineNumber, document.getLineStartOffset(lineNumber));
            caretModel.moveToLogicalPosition(logical);
        }
    }

    /**
     * 查找Java方法
     * @param eqlFilePackageName eql文件对应的packageName
     * @param file Java对应文件的PsiFile
     * @param methodName 方法名称
     * @return 查找到的Java方法，没有则返回null
     */
    private PsiElement seekJavaMethod(String eqlFilePackageName, PsiFile file, String methodName) {
        String packageName = ((PsiJavaFile) file).getPackageName();
        if (!eqlFilePackageName.equals(packageName)) return null;

        return findMethod(file, methodName);
    }

    private PsiElement findMethod(PsiElement psiElement, String methodName) {
        Stack<PsiElement> stack = new Stack<>();
        stack.push(psiElement);
        while (!stack.empty()) {
            PsiElement element = stack.pop();
            if (element instanceof PsiIdentifier) {
                String methodText = element.getText();
                if (methodName.equals(methodText)) return element;
            }
            Stream.of(element.getChildren()).forEach(stack::push);
        }
        return null;
    }
}

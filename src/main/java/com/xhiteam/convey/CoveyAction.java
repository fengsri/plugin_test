package com.xhiteam.convey;

import com.goide.psi.impl.GoFunctionDeclarationImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class CoveyAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            // 获取当前项目和编辑器
            Project project = e.getProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor != null) {
                PsiFile psiFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE);
                if (psiFile == null) {
                    Messages.showMessageDialog(e.getProject(), "未找到当前文件", "错误", Messages.getErrorIcon());
                    return;
                }

                // 1. 获取当前文件信息 / 读取文件信息
                String filePath = psiFile.getVirtualFile().getPath();
                if (!Strings.endsWith(filePath, ".go")) {
                    Messages.showMessageDialog(e.getProject(), "请选择Go文件执行", "Covey", Messages.getErrorIcon());
                    return;
                }

                // 3. 获取当前光标的行
                String row = getRowText(editor);
                String tmp = Strings.trim(row);
                if (!tmp.startsWith("Convey(") && !tmp.startsWith("FocusConvey(")) {
                    Messages.showMessageDialog(e.getProject(), "请选择子用例代码行", "Covey", Messages.getErrorIcon());
                    return;
                }

                String fileContent = getFileInfo(filePath);
                if (fileContent.isEmpty()) {
                    return;
                }

                // 2. 读取当前光标所在函数的Covey用例代码
                String conveyFuncTxt = getTddConvey(psiFile, editor.getCaretModel().getOffset());
                if (conveyFuncTxt.isEmpty()) {
                    return;
                }
                int funcRow = getTddFuncRowNum(psiFile, conveyFuncTxt);
                if (funcRow == 0) {
                    return;
                }

                // 4. 解析并替换子父级的Covey
                LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
                int lineNumber = logicalPosition.line;
                String result = new CoveyAnalysis().createConveyTree(conveyFuncTxt, new RowString(row, lineNumber), funcRow);
                result = fileContent.replace(conveyFuncTxt, result);

                // 5. 写入文件内容
                writeFileInfo(e, filePath, result);
                psiFile.getVirtualFile().refresh(false, false);

                Messages.showMessageDialog(e.getProject(), "Covey替换成功", "Covey", Messages.getInformationIcon());
            } else {
                System.out.println("没有找到当前编辑器");
            }
        } catch (Exception exception) {
            Messages.showMessageDialog(e.getProject(), "Covey替换报错：" + exception.toString(), "Covey", Messages.getErrorIcon());
        }
    }

    public String getFileInfo(String path) {
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeFileInfo(AnActionEvent e, String path, String data) {
        try {
            Files.writeString(Paths.get(path), data);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getTddConvey(PsiFile psiFile, int caretOffset) {
        PsiElement method = getTddConveyFucElement(psiFile.findElementAt(caretOffset));
        if (method == null) {
            return "";
        }
        return method.getText();
    }

    public int getTddFuncRowNum(PsiFile psiFile, String func) {
        String[] tempList = func.split("\n");
        if (tempList.length == 0) {
            return 0;
        }
        String funcName = tempList[0];
        String[] tempList2 = psiFile.getText().split("\n");
        int num = 0;
        for (int i = 0; i < tempList2.length; i++) {
            if (funcName.equals(tempList2[i])) {
                num = i;
                break;
            }
        }
        return num;
    }

    public String getRowText(Editor editor) {
        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        int lineNumber = logicalPosition.line;
//        Messages.showInfoMessage(e.getProject(), txt, "读取当前行");
        return editor.getDocument().getText(new com.intellij.openapi.util.TextRange(
                editor.getDocument().getLineStartOffset(lineNumber),
                editor.getDocument().getLineEndOffset(lineNumber)
        ));
    }

    public String getText(AnActionEvent e, Editor editor) {
        int caretOffset = editor.getCaretModel().getOffset();
        PsiFile psiFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE);
        if (psiFile != null) {
            PsiElement element = psiFile.findElementAt(caretOffset);
            PsiElement method = getTddConveyFucElement(element);
            if (method != null) {
                String methodCode = method.getText();
                Messages.showMessageDialog(e.getProject(), "当前函数代码:\n" + methodCode, "函数代码", Messages.getInformationIcon());
            } else {
                Messages.showMessageDialog(e.getProject(), "光标不在任何函数内部", "错误", Messages.getErrorIcon());
            }
        } else {
            Messages.showMessageDialog(e.getProject(), "没有找到当前文件", "错误", Messages.getErrorIcon());
        }
        return "";
    }

    public PsiElement getTddConveyFucElement(PsiElement element) {
        if (element == null) {
            return null;
        }
//        String txt = element.getText();
//        String[] lineList = txt.split("\n");
//        if (lineList.length > 2) {
//            String firstLine = Strings.trim(lineList[0]);
//            String lastLine = Strings.trim(lineList[lineList.length - 1]);
//            if (firstLine.equals("{") && lastLine.equals("}")) {
//                PsiElement a = element.getParent();
//                String aa = a.getText();
//                PsiElement b = a.getParent();
//                String bb = b.getText();
//                return element;
//            }
//        }
        // 寻找函数
        String txt = element.getText();
        if (element instanceof GoFunctionDeclarationImpl) {
            return element;
        }
        return getTddConveyFucElement(element.getParent());
    }


}

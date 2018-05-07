package com.jas.componentintention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testIntegration.TestCreator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

@NonNls
public class CreateComponentTestIntention  implements TestCreator {


    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        final int offset = editor.getCaretModel().getOffset();
        PsiElement element = findElement(file, offset);
        return CreateComponentTestAction.isAvailableForElement(element);
    }

    @Override
    public void createTest(Project project, Editor editor, PsiFile file) {
        try {
            CreateComponentTestAction action = new CreateComponentTestAction();
            PsiElement element = findElement(file, editor.getCaretModel().getOffset());
            if (CreateComponentTestAction.isAvailableForElement(element)) {
                action.invoke(project, editor, element);
            }
        }
        catch (IncorrectOperationException e) {
            e.printStackTrace();
        }
    }


    private static PsiElement findElement(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null && offset == file.getTextLength()) {
            element = file.findElementAt(offset - 1);
        }
        return element;
    }
}

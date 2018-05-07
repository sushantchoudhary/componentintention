package com.jas.componentintention;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.testIntegration.createTest.CreateTestDialog;
import com.intellij.testIntegration.createTest.TestGenerator;
import com.intellij.testIntegration.createTest.TestGenerators;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.uast.UastUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateComponentTestAction extends PsiElementBaseIntentionAction {

    private static final String CREATE_TEST_IN_THE_SAME_ROOT = "create.test.in.the.same.root";

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        final Module srcModule = ModuleUtilCore.findModuleForPsiElement(element);
        if (srcModule == null) return;

        final PsiClass srcClass = UastUtils.getContainingClass(element);

        if (srcClass == null) return;

        PsiDirectory srcDir = element.getContainingFile().getContainingDirectory();
        PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
        Module testModule = CreateTestAction.suggestModuleForTests(project, srcModule);
        final List<VirtualFile> testRootUrls = computeTestRoots(testModule);
        if (testRootUrls.isEmpty() && computeSuitableTestRootUrls(testModule).isEmpty()) {
            testModule = srcModule;
            if (!propertiesComponent.getBoolean(CREATE_TEST_IN_THE_SAME_ROOT)) {
                if (Messages.showOkCancelDialog(project, "Create test in the same source root?", "No Test Roots Found", Messages.getQuestionIcon()) !=
                        Messages.OK) {
                    return;
                }
                propertiesComponent.setValue(CREATE_TEST_IN_THE_SAME_ROOT, true);
            }
        }

        final CreateTestDialog d = createTestDialog(project, testModule, srcClass, srcPackage);
        if (!d.showAndGet()) {
            return;
        }

        CommandProcessor.getInstance().executeCommand(project, () -> {
            TestFramework framework = d.getSelectedTestFrameworkDescriptor();
            final TestGenerator generator = TestGenerators.INSTANCE.forLanguage(framework.getLanguage());
            DumbService.getInstance(project).withAlternativeResolveEnabled(() -> generator.generateTest(project, d));
        }, "Create Component Test", this);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        if (!isAvailableForElement(element)) return false;

        PsiClass psiClass = UastUtils.getContainingClass(element);

        assert psiClass != null;
        PsiElement leftBrace = psiClass.getLBrace();
        if (leftBrace == null) return false;
        if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

        //TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
        //if (!declarationRange.contains(element.getTextRange())) return false;

        return true;
    }

    public static boolean isAvailableForElement(PsiElement element) {
        if (Extensions.getExtensions(TestFramework.EXTENSION_NAME).length == 0) return false;

        if (element == null) return false;

        PsiClass psiClass = UastUtils.getContainingClass(element);

        if (psiClass == null) return false;

        PsiFile file = psiClass.getContainingFile();
        if (file.getContainingDirectory() == null || JavaProjectRootsUtil.isOutsideJavaSourceRoot(file)) return false;

        if (psiClass.isAnnotationType() ||
                psiClass instanceof PsiAnonymousClass) {
            return false;
        }

        return TestFrameworks.detectFramework(psiClass) == null;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return getText();
    }

    @Override
    @NotNull
    public String getText() {
        return "Create Component Test";
    }

    protected static List<VirtualFile> computeTestRoots(@NotNull Module mainModule) {
        if (!computeSuitableTestRootUrls(mainModule).isEmpty()) {
            //create test in the same module, if the test source folder doesn't exist yet it will be created
            return suitableTestSourceFolders(mainModule)
                    .map(SourceFolder::getFile)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        //suggest to choose from all dependencies modules
        final HashSet<Module> modules = new HashSet<>();
        ModuleUtilCore.collectModulesDependsOn(mainModule, modules);
        return modules.stream()
                .flatMap(CreateComponentTestAction::suitableTestSourceFolders)
                .map(SourceFolder::getFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected CreateTestDialog createTestDialog(Project project, Module srcModule, PsiClass srcClass, PsiPackage srcPackage) {
        return new CreateTestDialog(project, getText(), srcClass, srcPackage, srcModule);
    }

    static List<String> computeSuitableTestRootUrls(@NotNull Module module) {
        return suitableTestSourceFolders(module).map(SourceFolder::getUrl).collect(Collectors.toList());
    }

    private static Stream<SourceFolder> suitableTestSourceFolders(@NotNull Module module) {
        Predicate<SourceFolder> forGeneratedSources = JavaProjectRootsUtil::isForGeneratedSources;
        return Arrays.stream(ModuleRootManager.getInstance(module).getContentEntries())
                .flatMap(entry -> entry.getSourceFolders(JavaSourceRootType.TEST_SOURCE).stream())
                .filter(forGeneratedSources.negate());
    }

}

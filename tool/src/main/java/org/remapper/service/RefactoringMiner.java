package org.remapper.service;

import gr.uom.java.xmi.diff.UMLModelDiff;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.remapper.util.GitServiceImpl;

import java.util.ArrayList;
import java.util.List;

public class RefactoringMiner {

    public List<Refactoring> discover(String projectPath, String commitId) throws Exception {
        GitService gitService = new GitServiceImpl();
        GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
        List<Refactoring> list = new ArrayList<>();
        try (Repository repo = gitService.openRepository(projectPath)) {
            detector.detectAtCommit(repo, commitId, new RefactoringHandler() {
                @Override
                public void handle(String commitId, UMLModelDiff modelDiff, List<Refactoring> refactorings) {
                    for (Refactoring ref : refactorings) {
                        if (ref.getRefactoringType() == RefactoringType.RENAME_METHOD ||
                                ref.getRefactoringType() == RefactoringType.CHANGE_RETURN_TYPE ||
                                ref.getRefactoringType() == RefactoringType.RENAME_ATTRIBUTE ||
                                ref.getRefactoringType() == RefactoringType.CHANGE_ATTRIBUTE_TYPE ||
                                ref.getRefactoringType() == RefactoringType.RENAME_CLASS ||
                                ref.getRefactoringType() == RefactoringType.MOVE_CLASS ||
                                ref.getRefactoringType() == RefactoringType.EXTRACT_OPERATION ||
                                ref.getRefactoringType() == RefactoringType.EXTRACT_CLASS ||
                                ref.getRefactoringType() == RefactoringType.INLINE_OPERATION)
                            list.add(ref);
                    }
                }
            });
        }
        return list;
    }
}

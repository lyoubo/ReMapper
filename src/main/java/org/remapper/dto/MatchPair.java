package org.remapper.dto;

import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MatchPair {

    /**
     * Software Entities
     */
    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> unchangedEntities;
    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities;
    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities;
    private Set<DeclarationNodeTree> deletedEntities;
    private Set<DeclarationNodeTree> addedEntities;
    private Set<DeclarationNodeTree> inlinedEntities;
    private Set<DeclarationNodeTree> extractedEntities;

    /**
     * Method Statements
     */
    private Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements;
    private Set<Pair<StatementNodeTree, StatementNodeTree>> candidateStatements;
    private Set<StatementNodeTree> deletedStatements;
    private Set<StatementNodeTree> addedStatements;

    public MatchPair() {
        unchangedEntities = new LinkedHashSet<>();
        matchedEntities = new LinkedHashSet<>();
        candidateEntities = new LinkedHashSet<>();
        deletedEntities = new LinkedHashSet<>();
        addedEntities = new LinkedHashSet<>();
        inlinedEntities = new LinkedHashSet<>();
        extractedEntities = new LinkedHashSet<>();

        matchedStatements = new LinkedHashSet<>();
        candidateStatements = new LinkedHashSet<>();
        deletedStatements = new LinkedHashSet<>();
        addedStatements = new LinkedHashSet<>();
    }

    /**
     * Methods related to the matching between software entities
     *
     * @return
     */
    public Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> getUnchangedEntities() {
        return unchangedEntities;
    }

    public void addUnchangedEntity(DeclarationNodeTree entityBefore, DeclarationNodeTree entityCurrent) {
        unchangedEntities.add(Pair.of(entityBefore, entityCurrent));
    }

    public void addUnchangedEntities(Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> filteredEntities) {
        this.unchangedEntities.addAll(filteredEntities);
    }

    public Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> getMatchedEntities() {
        return matchedEntities;
    }

    public Set<Pair<EntityInfo, EntityInfo>> getMatchedEntityInfos() {
        return matchedEntities.stream().map(pair -> Pair.of(pair.getLeft().getEntity(), pair.getRight().getEntity())).collect(Collectors.toSet());
    }

    public Set<DeclarationNodeTree> getMatchedEntitiesLeft() {
        return matchedEntities.stream().map(Pair::getLeft).collect(Collectors.toSet());
    }

    public Set<DeclarationNodeTree> getMatchedEntitiesRight() {
        return matchedEntities.stream().map(Pair::getRight).collect(Collectors.toSet());
    }

    public void addMatchedEntity(DeclarationNodeTree entityBefore, DeclarationNodeTree entityCurrent) {
        matchedEntities.add(Pair.of(entityBefore, entityCurrent));
    }

    public Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> getCandidateEntities() {
        return candidateEntities;
    }

    public Set<Pair<EntityInfo, EntityInfo>> getCandidateEntityInfos() {
        return candidateEntities.stream().map(pair -> Pair.of(pair.getLeft().getEntity(), pair.getRight().getEntity())).collect(Collectors.toSet());
    }

    public Set<DeclarationNodeTree> getCandidateEntitiesLeft() {
        return candidateEntities.stream().map(Pair::getLeft).collect(Collectors.toSet());
    }

    public Set<DeclarationNodeTree> getCandidateEntitiesRight() {
        return candidateEntities.stream().map(Pair::getRight).collect(Collectors.toSet());
    }

    public void addCandidateEntity(DeclarationNodeTree entityBefore, DeclarationNodeTree entityCurrent) {
        candidateEntities.add(Pair.of(entityBefore, entityCurrent));
    }

    public void setCandidateEntities(Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities) {
        this.candidateEntities = candidateEntities;
    }

    public Set<DeclarationNodeTree> getDeletedEntities() {
        return deletedEntities;
    }

    public void addDeletedEntities(List<DeclarationNodeTree> deletedEntities) {
        this.deletedEntities.addAll(deletedEntities);
    }

    public void updateDeletedEntities(Set<DeclarationNodeTree> entities) {
        deletedEntities.removeAll(getCandidateEntitiesLeft());
        deletedEntities.addAll(entities);
    }

    public Set<DeclarationNodeTree> getAddedEntities() {
        return addedEntities;
    }

    public void addAddedEntities(List<DeclarationNodeTree> addedEntities) {
        this.addedEntities.addAll(addedEntities);
    }

    public void updateAddedEntities(Set<DeclarationNodeTree> entities) {
        addedEntities.removeAll(getCandidateEntitiesRight());
        addedEntities.addAll(entities);
    }

    public Set<DeclarationNodeTree> getInlinedEntities() {
        return inlinedEntities;
    }

    public void setInlinedEntities(Set<DeclarationNodeTree> inlinedEntities) {
        this.inlinedEntities = inlinedEntities;
    }

    public Set<DeclarationNodeTree> getExtractedEntities() {
        return extractedEntities;
    }

    public void setExtractedEntities(Set<DeclarationNodeTree> extractedEntities) {
        this.extractedEntities = extractedEntities;
    }

    /**
     * Methods related to the matching between method statements
     *
     * @return
     */
    public Set<Pair<StatementNodeTree, StatementNodeTree>> getMatchedStatements() {
        return matchedStatements;
    }

    public void addMatchedStatement(StatementNodeTree statementBefore, StatementNodeTree statementCurrent) {
        matchedStatements.add(Pair.of(statementBefore, statementCurrent));
    }

    public Set<Pair<StatementNodeTree, StatementNodeTree>> getCandidateStatements() {
        return candidateStatements;
    }

    public void setCandidateStatements(Set<Pair<StatementNodeTree, StatementNodeTree>> candidateStatements) {
        this.candidateStatements = candidateStatements;
    }

    public Set<StatementNodeTree> getDeletedStatements() {
        return deletedStatements;
    }

    public void addDeletedStatements(List<StatementNodeTree> deletedStatements) {
        this.deletedStatements.addAll(deletedStatements);
    }

    public void addDeletedStatement(StatementNodeTree deletedStatement) {
        this.deletedStatements.add(deletedStatement);
    }

    public Set<StatementNodeTree> getAddedStatements() {
        return addedStatements;
    }

    public void addAddedStatements(List<StatementNodeTree> addedStatements) {
        this.addedStatements.addAll(addedStatements);
    }

    public void addAddedStatement(StatementNodeTree addedStatement) {
        this.addedStatements.add(addedStatement);
    }
}

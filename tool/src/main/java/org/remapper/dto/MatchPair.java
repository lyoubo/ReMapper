package org.remapper.dto;

import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MatchPair {

    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> unchangedEntities;
    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities;
    private Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities;
    private Set<DeclarationNodeTree> deletedEntities;
    private Set<DeclarationNodeTree> addedEntities;

    public MatchPair() {
        unchangedEntities = new LinkedHashSet<>();
        matchedEntities = new LinkedHashSet<>();
        candidateEntities = new LinkedHashSet<>();
        deletedEntities = new LinkedHashSet<>();
        addedEntities = new LinkedHashSet<>();
    }

    public Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> getUnchangedEntities() {
        return unchangedEntities;
    }

    public void addUnchangedEntity(DeclarationNodeTree entityBefore, DeclarationNodeTree entityCurrent) {
        unchangedEntities.add(Pair.of(entityBefore, entityCurrent));
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

    public void addUnchangedEntities(Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> filteredEntities) {
        this.unchangedEntities.addAll(filteredEntities);
    }
}

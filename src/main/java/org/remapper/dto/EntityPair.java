package org.remapper.dto;

public class EntityPair implements Comparable<EntityPair> {

    private final DeclarationNodeTree dntBefore;
    private final DeclarationNodeTree dntCurrent;
    private double dice;

    public EntityPair(DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        this.dntBefore = dntBefore;
        this.dntCurrent = dntCurrent;
    }

    public DeclarationNodeTree getLeft() {
        return dntBefore;
    }

    public DeclarationNodeTree getRight() {
        return dntCurrent;
    }

    public void setDice(double dice) {
        this.dice = dice;
    }

    @Override
    public int compareTo(EntityPair o) {
        return Double.compare(o.dice, this.dice);
    }
}

package diecup;

import java.util.concurrent.ThreadLocalRandom;

public class Die {
    private int faceValue;
    private int sides;

    public Die(int sides) {
        this.sides = sides;
        this.faceValue = 1;
        roll();
    }

    public void roll() {
        this.faceValue = ThreadLocalRandom.current().nextInt(this.sides) + 1;
    }

    public int getFaceValue() {
        return this.faceValue;
    }

    public String toString() {
        return "Die with " + this.sides + " sides, value: " + this.faceValue;
    }
}

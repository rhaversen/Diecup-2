package diecup;

import java.util.Random;

public class Die {
    private int faceValue;
    private int sides;
    private Random random = new Random();

    public Die(int sides) {
        this.sides = sides;
        this.faceValue = 1;
        roll();
    }

    public void roll() {
        this.faceValue = random.nextInt(this.sides) + 1;
    }

    public int getFaceValue() {
        return this.faceValue;
    }

    public String toString() {
        return "Die with " + this.sides + " sides, value: " + this.faceValue;
    }
}

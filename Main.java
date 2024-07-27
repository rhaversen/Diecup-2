import diecup.Game;
import strategies.WeightedSelect;

public class Main {
    public static void main(String[] args) {
        int numberOfDice = 6;
        int sides = 6;
        Game game = new Game(numberOfDice, sides, new WeightedSelect(numberOfDice, sides));
        game.startGame();
    }
}


import diecup.Calculations;
import diecup.Game;
import strategies.*;

public class Main {
    public static void main(String[] args) {
        if ("start".equals(args[0])) {
            startGame();
        } else if ("statistics".equals(args[0])) {
            getStatistics();
        }
    }

    public static void getStatistics() {
        int numberOfDice = 6;
        int sides = 6;
        Calculations calculations = new Calculations();
        calculations.calculateAverageTurns(new AdvancedWeightedSelect(numberOfDice, sides));
    }

    public static void startGame() {
        int numberOfDice = 6;
        int sides = 6;
        Game game = new Game(numberOfDice, sides, new AdvancedWeightedSelect(numberOfDice, sides), true, true);
        game.startGame();
    }
}


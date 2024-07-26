public class Main {
    public static void main(String[] args) {
        Game game = new Game(5, 6, new SelectHighest());
        game.startGame();
        System.out.println("Total turns: " + game.getTurns());
    }
}
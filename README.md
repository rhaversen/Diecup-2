# Diecup-2 Game Objective

Win by collecting 5 points for each number (1-12) on the scoreboard. The final score is measured by how many turns it takes to complete this goal.

## Setup

- Scoreboard contains numbers 1 through 12, each requiring 5 points to complete
- Start each turn with 6 dice

## Turn Mechanics

1. **Roll Phase**: Roll all available dice (starting with 5)
2. **Selection Phase**: Pick a target number (1–12):
    - **Numbers 1-6**: Take all dice showing that face.
    - **Numbers 7-12**: Take all available disjoint pairs summing to that number.
    - **Note**: Once chosen, you must stick with this target for every subsequent roll in the turn. You must collect all available dice for the number you initially selected in a roll. You cant leave any dice with the same value as the number you selected in a roll.
3. **Scoring**: Score points equal to the number of dice or pair of dice collected for that slot.
4. **Continue**: Remove collected dice and reroll the remaining.
5. **Repeat**: Repeat steps 2–4 until no valid combination remains or all dice are used. You can only select dice that match the number you initially selected in a turn. For example, if you select the number 3, you can only collect dice with the value of 3 in that roll.

## Special Rules

- **Completion Bonus**: When any number reaches 5 points, all dice return to the cup and you get a free turn, allowing you to select any number to start the next turn.
- **All Dice Bonus**: If you use all dice in a turn, all dice return to the cup for the next selection and you get a free turn, allowing you to select any number to start the next turn.
- **Turn End**: A turn ends when no valid dice combinations can be made. Valid combinations means you can select at least one die or pair of dice to score points for a number on the scoreboard which has not yet reached 5 points.

## Winning Condition

The game ends when all numbers (1-12) have reached 5 points each. Your score is the total number of turns taken to achieve this.

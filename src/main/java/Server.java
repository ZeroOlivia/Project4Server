import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

public class Server {

    private static final int PORT = 5555;
    private static final int BOARD_SIZE = 10;
    private static final char EMPTY_CELL = '-';
    private static final char SHIP_CELL = 'S';
    private static final char HIT_CELL = 'X';
    private static final char MISS_CELL = 'O';

    private static char[][] playerBoard;
    private static char[][] aiBoard;
    private static boolean bombingUnlocked = false;
    private static int playerHits = 0;
    private static int aiHits = 0;
    private static final Object hitsLock = new Object();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                initializeBoards();
                setupPlayerShips(clientSocket);

                // handleGame(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initializeBoards() {
        playerBoard = new char[BOARD_SIZE][BOARD_SIZE];
        aiBoard = new char[BOARD_SIZE][BOARD_SIZE];
        for (char[] row : playerBoard) {
            Arrays.fill(row, EMPTY_CELL);
        }
        for (char[] row : aiBoard) {
            Arrays.fill(row, EMPTY_CELL);
        }
        setupAIShips();
    }

    private static void setupPlayerShips(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            out.println("Welcome to Battleship! Place your ships on the board.");
            out.println("Enter ship coordinates and direction (e.g., 'A1 horizontal')");

            // Let the player place their ships
            for (int shipLength = 2; shipLength <= 5; shipLength++) {
                out.println("Place your ship of length " + shipLength + ":");
                String[] input = in.readLine().split(" ");
                int row = input[0].toUpperCase().charAt(0) - 'A';
                int col = Integer.parseInt(input[0].substring(1)) - 1;
                String direction = input[1].toLowerCase();
                if (isValidPlacement(playerBoard, row, col, direction, shipLength)) {
                    placeShip(playerBoard, row, col, direction, shipLength);
                } else {
                    out.println("Invalid placement! Try again.");
                    shipLength--; // Retry placing the ship
                }
            }
            handleGame(clientSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setupAIShips() {
        Random random = new Random();
        for (int shipLength = 2; shipLength <= 5; shipLength++) {
            int row, col;
            String direction;
            do {
                row = random.nextInt(BOARD_SIZE);
                col = random.nextInt(BOARD_SIZE);
                direction = random.nextBoolean() ? "horizontal" : "vertical";
            } while (!isValidPlacement(aiBoard, row, col, direction, shipLength));
            placeShip(aiBoard, row, col, direction, shipLength);
        }
    }

    private static boolean isValidPlacement(char[][] board, int row, int col, String direction, int length) {
        if (row < 0 || col < 0 || row >= BOARD_SIZE || col >= BOARD_SIZE) {
            return false;
        }
        if (direction.equals("horizontal")) {
            if (col + length > BOARD_SIZE) {
                return false;
            }
            for (int i = col; i < col + length; i++) {
                if (board[row][i] == SHIP_CELL) {
                    return false;
                }
            }
        } else if (direction.equals("vertical")) {
            if (row + length > BOARD_SIZE) {
                return false;
            }
            for (int i = row; i < row + length; i++) {
                if (board[i][col] == SHIP_CELL) {
                    return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }

    private static void placeShip(char[][] board, int row, int col, String direction, int length) {
        if (direction.equals("horizontal")) {
            for (int i = col; i < col + length; i++) {
                board[row][i] = SHIP_CELL;
            }
        } else if (direction.equals("vertical")) {
            for (int i = row; i < row + length; i++) {
                board[i][col] = SHIP_CELL;
            }
        }
    }

    private static void handleGame(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            out.println("Setup completed. Your board looks like this!");
            printBoard(out, playerBoard, false);
            out.println("Let the game begin!");
            while (!isGameOver()) {
                // Player's turn
                out.println("Your turn! Enter target coordinates (e.g., 'A1'):");
                String target = in.readLine();
                int row = target.toUpperCase().charAt(0) - 'A';
                int col = Integer.parseInt(target.substring(1)) - 1;
                if (aiBoard[row][col] == SHIP_CELL) {
                    out.println("Hit!");
                    aiBoard[row][col] = HIT_CELL;
                    incrementPlayerHits();
                } else {
                    out.println("Miss!");
                    aiBoard[row][col] = MISS_CELL;
                }
                out.println("AI board:");
                printBoard(out, aiBoard, true);

                if (playerHits == 3) {
                    out.println("You've unlocked the bombing option!");
                    bombingUnlocked = true;
                    out.println("Do you want to bomb a row or column? (row/column)");
                    String bombChoice = in.readLine().toLowerCase();
                    if (bombChoice.equals("column")) {
                        out.println("Enter the column (1-10) to bomb:");
                        int bombCol = Integer.parseInt(in.readLine()) - 1;
                        bombCol(bombCol, aiBoard);
                    } else if (bombChoice.equals("row")) {
                        out.println("Enter the row (A-J) to bomb:");
                        int bombRow = in.readLine().toUpperCase().charAt(0) - 'A';
                        bombRow(bombRow, aiBoard);
                    }
                    // Reset player hits after using the bombing option
                    resetHitsPlayer();
                }

                // AI's turn
                int aiRow = generateRandomNumber(BOARD_SIZE);
                int aiCol = generateRandomNumber(BOARD_SIZE);
                out.println("AI chose: " + (char) ('A' + aiRow) + (aiCol + 1));
                if (playerBoard[aiRow][aiCol] == SHIP_CELL) {
                    out.println("AI Hit!");
                    playerBoard[aiRow][aiCol] = HIT_CELL;
                    incrementAiHits();
                    if(aiHits == 3){
                        bombRandomRow();
                        resetHitsAI();
                    }
                } else {
                    out.println("AI Miss!");
                    playerBoard[aiRow][aiCol] = MISS_CELL;
                }
                out.println("Your board:");
                printBoard(out, playerBoard, false);
            }



            // Determine the winner
            if (isGameOver()) {
                out.println("Game Over!");
                if (allShipsSunk(aiBoard)) {
                    out.println("You Win!");
                } else {
                    out.println("You Lose!");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isGameOver() {
        return allShipsSunk(playerBoard) || allShipsSunk(aiBoard);
    }

    private static boolean allShipsSunk(char[][] board) {
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == SHIP_CELL) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int generateRandomNumber(int max) {
        return (int) (Math.random() * max);
    }
    private static void bombRow(int row, char[][] targetBoard) {
        for (int j = 0; j < BOARD_SIZE; j++) {
            if (targetBoard[row][j] == SHIP_CELL) {
                targetBoard[row][j] = HIT_CELL;
            }
            else{
                targetBoard[row][j] = MISS_CELL;
            }
        }
    }

    private static void bombCol(int col, char[][] targetBoard) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (targetBoard[i][col] == SHIP_CELL) {
                targetBoard[i][col] = HIT_CELL;
            }
            else{
                targetBoard[i][col] = MISS_CELL;
            }
        }
    }

    private static void bombRandomRow() {
        int row = generateRandomNumber(BOARD_SIZE);
        bombRow(row, aiBoard);
    }

    private static void bombRandomColumn(char[][] targetBoard) {
        int col = generateRandomNumber(BOARD_SIZE);
        bombCol(col, targetBoard);
    }

    private static void printBoard(PrintWriter out, char[][] board, boolean hideShips) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                char cell = board[i][j];
                if (cell == HIT_CELL || cell == MISS_CELL || !hideShips) {
                    out.print(cell + " ");
                } else {
                    out.print(EMPTY_CELL + " ");
                }
            }
            out.println();
        }
    }



    private static void incrementPlayerHits() {
        synchronized (hitsLock) {
            playerHits++;
        }
    }

    private static void incrementAiHits() {
        synchronized (hitsLock) {
            aiHits++;
        }
    }


    private static void resetHitsAI() {
        synchronized (hitsLock) {
            aiHits = 0;
        }
    }
    private static void resetHitsPlayer() {
        synchronized (hitsLock) {
            playerHits = 0;
        }
    }


    private static int countShipsSunk(char[][] board) {
        int shipCount = 0;
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == HIT_CELL) {
                    shipCount++;
                }
            }
        }
        return shipCount;
    }

}

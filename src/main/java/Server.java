public class Server {
   private static final int PORT = 5555;
    private static final int BOARD_SIZE = 10;
    private static final char EMPTY_CELL = '-';
    private static final char SHIP_CELL = 'S';
    private static final char HIT_CELL = 'X';
    private static final char MISS_CELL = 'O';

    private static char[][] board;
    private static List<Socket> clients = new ArrayList<>();
    private static int currentPlayer = 0;
    private static boolean gameStarted = false;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running...");
            initializeBoard();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);
                clients.add(clientSocket);

                if (!gameStarted) {
                    gameStarted = true;
                    notifyAllClients("Welcome to Battleship! You are playing against the AI.");
                }

                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initializeBoard() {
        board = new char[BOARD_SIZE][BOARD_SIZE];
        for (char[] row : board) {
            Arrays.fill(row, EMPTY_CELL);
        }
        placeShips();
    }

    private static synchronized void notifyAllClients(String message) {
        for (Socket client : clients) {
            try {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                out.println(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private static void placeShips() {
        Random random = new Random();
        int numShips = 5; // Let's say we have 5 ships
        for (int i = 0; i < numShips; i++) {
            int row = random.nextInt(BOARD_SIZE);
            int col = random.nextInt(BOARD_SIZE);
            if (board[row][col] != SHIP_CELL) {
                board[row][col] = SHIP_CELL;
            } else {
                i--; // Retry placing the ship
            }
        }
    }
    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            while (!isGameOver()) {
                synchronized (clients) {
                    // Player's turn
                    if (clients.indexOf(clientSocket) == currentPlayer) {
                        out.println("Your turn! Enter target coordinates (row col): ");
                        String[] target = in.readLine().split(" ");
                        int row = Integer.parseInt(target[0]);
                        int col = Integer.parseInt(target[1]);

                        if (board[row][col] == SHIP_CELL) {
                            out.println("Hit!");
                            board[row][col] = HIT_CELL;
                        } else {
                            out.println("Miss!");
                            board[row][col] = MISS_CELL;
                        }
                        notifyAllClients(printBoard()); // Notify all clients about the current board state
                        //currentPlayer = (currentPlayer + 1) % clients.size();
                        int aiRow = generateRandomNumber(BOARD_SIZE);
                        int aiCol = generateRandomNumber(BOARD_SIZE);

                        if (board[aiRow][aiCol] == SHIP_CELL) {
                            out.println("AI chose: " + aiRow + " " + aiCol + " Hit!");
                            board[aiRow][aiCol] = HIT_CELL;
                        } else {
                            out.println("AI chose: " + aiRow + " " + aiCol + " Miss!");
                            board[aiRow][aiCol] = MISS_CELL;
                        }
                        notifyAllClients(printBoard()); // Notify all clients about the current board state
                        currentPlayer = (currentPlayer + 1) % clients.size();
                    }
                    // AI's turn
                    else {
                        int aiRow = generateRandomNumber(BOARD_SIZE);
                        int aiCol = generateRandomNumber(BOARD_SIZE);

                        if (board[aiRow][aiCol] == SHIP_CELL) {
                            out.println("AI chose: " + aiRow + " " + aiCol + " Hit!");
                            board[aiRow][aiCol] = HIT_CELL;
                        } else {
                            out.println("AI chose: " + aiRow + " " + aiCol + " Miss!");
                            board[aiRow][aiCol] = MISS_CELL;
                        }
                        notifyAllClients(printBoard()); // Notify all clients about the current board state
                        currentPlayer = (currentPlayer + 1) % clients.size();

                    }
                }
            }
            // Determine the winner
            if (isGameOver()) {
                out.println("Game Over! You Win!");
                notifyAllClients("Game Over! You Lose!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isGameOver() {
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

    private static String printBoard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                char cellToShow = board[i][j];
                if (cellToShow == SHIP_CELL && cellToShow != HIT_CELL) {
                    cellToShow = EMPTY_CELL; // Hide ships unless they are hit
                }
                sb.append(cellToShow).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

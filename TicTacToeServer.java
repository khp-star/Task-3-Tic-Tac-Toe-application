import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TicTacToeServer {

    // Game State Variables
    private static String[] board = {"", "", "", "", "", "", "", "", ""};
    private static String currentPlayer = "X";
    private static String mode = "PVP"; // "PVP" (Player vs Player) or "PVC" (Player vs Computer)
    private static String status = "Player X's Turn";
    private static boolean gameOver = false;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Serve HTML and CSS
        server.createContext("/", new FileHandler("index.html", "text/html"));
        server.createContext("/style.css", new FileHandler("style.css", "text/css"));

        // API Endpoints for Game Logic
        server.createContext("/api/state", exchange -> sendGameState(exchange));
        server.createContext("/api/move", exchange -> handleMove(exchange));
        server.createContext("/api/reset", exchange -> handleReset(exchange));

        server.setExecutor(null);
        server.start();
        System.out.println("Server started! Open http://localhost:8080 in your browser.");
    }

    // Handles player clicks and optional computer response
    private static void handleMove(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery(); // e.g., "index=4"
        int index = Integer.parseInt(query.split("=")[1]);

        if (!gameOver && index >= 0 && index < 9 && board[index].isEmpty()) {
            // 1. Apply Human Move
            board[index] = currentPlayer;
            checkGameState();

            // 2. If PvC mode and game isn't over, trigger Computer ('O') move
            if (!gameOver && mode.equals("PVC") && currentPlayer.equals("O")) {
                makeComputerMove();
                checkGameState();
            }
        }
        sendGameState(exchange);
    }

    // Simple AI: Tries to win, blocks player, or picks a random available spot
    private static void makeComputerMove() {
        int move = findWinningMove("O"); // Try to win
        if (move == -1) move = findWinningMove("X"); // Block X from winning
        if (move == -1) {
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                if (board[i].isEmpty()) available.add(i);
            }
            if (!available.isEmpty()) {
                move = available.get(new Random().nextInt(available.size()));
            }
        }
        if (move != -1) {
            board[move] = "O";
        }
    }

    private static int findWinningMove(String player) {
        int[][] winPatterns = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Cols
            {0, 4, 8}, {2, 4, 6}             // Diagonals
        };
        for (int[] p : winPatterns) {
            if (board[p[0]].equals(player) && board[p[1]].equals(player) && board[p[2]].isEmpty()) return p[2];
            if (board[p[0]].equals(player) && board[p[2]].equals(player) && board[p[1]].isEmpty()) return p[1];
            if (board[p[1]].equals(player) && board[p[2]].equals(player) && board[p[0]].isEmpty()) return p[0];
        }
        return -1;
    }

    // Checks win or draw conditions and switches turns
    private static void checkGameState() {
        int[][] winPatterns = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
        };

        for (int[] p : winPatterns) {
            if (!board[p[0]].isEmpty() && board[p[0]].equals(board[p[1]]) && board[p[0]].equals(board[p[2]])) {
                status = (mode.equals("PVC") && currentPlayer.equals("O")) 
                         ? "Computer (O) Wins!" 
                         : "Player " + currentPlayer + " Wins!";
                gameOver = true;
                return;
            }
        }

        boolean isDraw = true;
        for (String cell : board) {
            if (cell.isEmpty()) {
                isDraw = false;
                break;
            }
        }

        if (isDraw) {
            status = "It's a Draw!";
            gameOver = true;
        } else {
            currentPlayer = currentPlayer.equals("X") ? "O" : "X";
            status = (mode.equals("PVC") && currentPlayer.equals("O")) 
                     ? "Computer's Turn..." 
                     : "Player " + currentPlayer + "'s Turn";
        }
    }

    // Resets the board and sets mode (PVP or PVC)
    private static void handleReset(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("mode=")) {
            mode = query.split("=")[1];
        }
        board = new String[]{"", "", "", "", "", "", "", "", ""};
        currentPlayer = "X";
        status = "Player X's Turn";
        gameOver = false;
        sendGameState(exchange);
    }

    // Sends current state as a JSON response
    private static void sendGameState(HttpExchange exchange) throws IOException {
        StringBuilder boardJson = new StringBuilder("[");
        for (int i = 0; i < 9; i++) {
            boardJson.append("\"").append(board[i]).append("\"");
            if (i < 8) boardJson.append(",");
        }
        boardJson.append("]");

        String json = String.format(
            "{\"board\":%s,\"status\":\"%s\",\"gameOver\":%b,\"mode\":\"%s\"}",
            boardJson, status, gameOver, mode
        );

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Helper class to serve static HTML and CSS files
    static class FileHandler implements HttpHandler {
        private final String filePath;
        private final String contentType;

        public FileHandler(String filePath, String contentType) {
            this.filePath = filePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}   
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// Standard Java Imports
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

// JavaMail Imports (Requires javax.mail.jar)
import javax.mail.*;
import javax.mail.internet.*;

public class CafeWebServer {

    public static void main(String[] args) throws IOException {
        System.out.println("\n>>> STARTING CAFE SERVER (JavaMail Mode)...");

        // 1. Initialize Services
        EmailService emailService = new EmailService();
        InventoryService inventoryService = new InventoryService(emailService);
        AuthService authService = new AuthService();

        // 2. Load Data
        inventoryService.loadStock("inventory.csv");
        inventoryService.loadMenuFromJson("recipes.json");
        authService.loadUsers("users.csv");

        // 3. Start Server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Polymorphism: Registering different controllers
        server.createContext("/", new StaticContentController());
        server.createContext("/api/data", new DataController(inventoryService));
        server.createContext("/api/order", new OrderController(inventoryService));
        server.createContext("/api/login", new LoginController(authService));
        server.createContext("/api/restock", new RestockController(inventoryService));

        server.setExecutor(null);
        System.out.println(">>> SERVER READY at http://localhost:8080\n");
        server.start();
    }

    // ==========================================
    // PARENT CONTROLLER (INHERITANCE)
    // ==========================================
    static abstract class BaseController implements HttpHandler {
        protected void sendResponse(HttpExchange t, int statusCode, String response) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(statusCode, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        protected Map<String, String> parseQuery(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] entry = param.split("=");
                    if (entry.length > 1) params.put(entry[0], entry[1]);
                }
            }
            return params;
        }
    }

    // ==========================================
    // SERVICES (ENCAPSULATION)
    // ==========================================

    // DIRECT EMAIL SERVICE (Uses javax.mail)
    static class EmailService {

        // --- !!! CONFIGURE THESE 3 LINES !!! ---
        private final String SENDER_EMAIL = "naimnuh1406@gmail.com";      // Your Gmail
        private final String APP_PASSWORD = "nyno phzm kqoq dskb";       // Your 16-char App Password
        private final String RECIPIENT_EMAIL = "214778@student.upm.edu.my"; // Who gets the alert?
        // ---------------------------------------

        public void sendLowStockAlert(String itemName, double currentStock, String unit) {
            // Run in a separate thread so the website doesn't freeze while sending email
            new Thread(() -> {
                System.out.println("   [EmailService] Preparing to send email via Google SMTP...");

                // 1. Setup Mail Server Properties
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");

                // 2. Authenticate
                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD);
                    }
                });

                try {
                    // 3. Create the Message
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(SENDER_EMAIL));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(RECIPIENT_EMAIL));

                    message.setSubject("URGENT: Low Stock Alert - " + itemName);
                    message.setText("Warning!\n\nThe item '" + itemName + "' is running low.\n"
                            + "Current Stock: " + currentStock + " " + unit + "\n\n"
                            + "Please restock immediately via the Admin Panel.");

                    // 4. Send
                    Transport.send(message);

                    System.out.println("   [EmailService] SUCCESS! Email sent to " + RECIPIENT_EMAIL);

                } catch (Exception e) {
                    System.out.println("   [EmailService] FAILED. Reason: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }
    }

    static class InventoryService {
        static class Item {
            int id; String name; double stock; String unit; double threshold;
            public Item(int id, String name, double stock, String unit, double threshold) {
                this.id = id; this.name = name; this.stock = stock; this.unit = unit; this.threshold = threshold;
            }
        }
        static class Recipe { int id; String name; double price; Map<Integer, Double> ingredients = new HashMap<>(); }

        private Map<Integer, Item> inventory = new HashMap<>();
        private Map<Integer, Recipe> menu = new LinkedHashMap<>();
        private EmailService emailService;
        private final String INVENTORY_FILE = "inventory.csv";

        public InventoryService(EmailService emailService) { this.emailService = emailService; }

        public void loadStock(String filename) {
            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(",");
                    int id = Integer.parseInt(p[0]);
                    inventory.put(id, new Item(id, p[1], Double.parseDouble(p[2]), p[3], Double.parseDouble(p[4])));
                }
                System.out.println("   [Inventory] Loaded " + inventory.size() + " stock items.");
            } catch (Exception e) { System.err.println("Error loading inventory"); }
        }

        public void loadMenuFromJson(String filename) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filename)));
                List<String> rawObjects = splitJsonObjects(content);
                for (String obj : rawObjects) {
                    try {
                        Recipe r = new Recipe();
                        r.id = Integer.parseInt(extractValue(obj, "\"id\":"));
                        r.name = extractValue(obj, "\"name\":").replace("\"", "");
                        r.price = Double.parseDouble(extractValue(obj, "\"price\":"));
                        int ingStart = obj.indexOf("\"ingredients\":");
                        if (ingStart != -1) {
                            int openBracket = obj.indexOf("[", ingStart);
                            int closeBracket = obj.indexOf("]", openBracket);
                            if (openBracket != -1) {
                                String ingBlock = obj.substring(openBracket + 1, closeBracket);
                                for (String rawIng : splitJsonObjects(ingBlock)) {
                                    int iId = Integer.parseInt(extractValue(rawIng, "\"id\":"));
                                    double qty = Double.parseDouble(extractValue(rawIng, "\"qty\":"));
                                    r.ingredients.put(iId, qty);
                                }
                            }
                        }
                        menu.put(r.id, r);
                    } catch(Exception ex) {}
                }
                System.out.println("   [Inventory] Loaded " + menu.size() + " recipes.");
            } catch (Exception e) { System.err.println("Error loading recipes JSON"); }
        }

        public synchronized boolean restockItem(int id, double qty) {
            Item item = inventory.get(id);
            if (item != null) {
                item.stock += qty;
                saveInventory();
                return true;
            }
            return false;
        }

        public synchronized String processOrder(int menuId) {
            Recipe r = menu.get(menuId);
            if (r == null) return error("Item not found");

            // Check availability
            for (Map.Entry<Integer, Double> ing : r.ingredients.entrySet()) {
                Item item = inventory.get(ing.getKey());
                if (item == null || item.stock < ing.getValue()) return error("Out of stock: " + (item!=null?item.name:"Unknown"));
            }

            // Deduct and Alert
            for (Map.Entry<Integer, Double> ing : r.ingredients.entrySet()) {
                Item item = inventory.get(ing.getKey());
                item.stock -= ing.getValue();
                if (item.stock <= item.threshold) emailService.sendLowStockAlert(item.name, item.stock, item.unit);
            }
            saveInventory();
            return success("Sold " + r.name);
        }

        public synchronized void saveInventory() {
            try (PrintWriter writer = new PrintWriter(new FileWriter(INVENTORY_FILE))) {
                writer.println("ID,Name,CurrentStock,Unit,LowStockThreshold");
                List<Integer> sortedIds = new ArrayList<>(inventory.keySet());
                Collections.sort(sortedIds);
                for (Integer id : sortedIds) {
                    Item item = inventory.get(id);
                    writer.printf("%d,%s,%.2f,%s,%.2f%n", item.id, item.name, item.stock, item.unit, item.threshold);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }

        public String getJsonData() {
            StringBuilder json = new StringBuilder("{ \"inventory\": [");
            int i = 0;
            for (Item item : inventory.values()) {
                if (i++ > 0) json.append(",");
                json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"stock\":%.2f,\"unit\":\"%s\",\"low\":%b}",
                        item.id, item.name, item.stock, item.unit, item.stock <= item.threshold));
            }
            json.append("], \"menu\": [");
            int j = 0;
            for (Recipe r : menu.values()) {
                if (j++ > 0) json.append(",");
                json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"price\":%.2f}", r.id, r.name, r.price));
            }
            json.append("]}");
            return json.toString();
        }

        private List<String> splitJsonObjects(String input) {
            List<String> result = new ArrayList<>();
            int braceCount = 0; int start = -1;
            for (int i = 0; i < input.length(); i++) {
                if (input.charAt(i) == '{') { if (braceCount == 0) start = i; braceCount++; }
                else if (input.charAt(i) == '}') { braceCount--; if (braceCount == 0 && start != -1) { result.add(input.substring(start, i + 1)); start = -1; } }
            }
            return result;
        }
        private String extractValue(String s, String k) {
            int st = s.indexOf(k); if (st == -1) return "0"; st += k.length();
            while (st < s.length() && (s.charAt(st)==' '||s.charAt(st)==':'||s.charAt(st)=='\n')) st++;
            int en = st; boolean q = false;
            while (en < s.length()) { if (s.charAt(en)=='"') q=!q; if(!q && (s.charAt(en)==','||s.charAt(en)=='}'||s.charAt(en)==']')) break; en++; }
            return s.substring(st, en).trim();
        }
        private String error(String msg) { return String.format("{\"success\":false,\"message\":\"%s\"}", msg); }
        private String success(String msg) { return String.format("{\"success\":true,\"message\":\"%s\"}", msg); }
    }

    static class AuthService {
        private Map<String, String[]> users = new HashMap<>();
        public void loadUsers(String filename) {
            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(",");
                    users.put(p[0], new String[]{p[1], p[2]});
                }
            } catch (Exception e) {}
        }
        public String verify(String u, String p) {
            if (users.containsKey(u) && users.get(u)[0].equals(p)) return users.get(u)[1];
            return null;
        }
    }

    // ==========================================
    // CONTROLLERS
    // ==========================================
    static class LoginController extends BaseController {
        private AuthService auth;
        public LoginController(AuthService auth) { this.auth = auth; }
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                String body = new BufferedReader(isr).readLine();
                if (body != null && body.contains(":")) {
                    String[] parts = body.split(":");
                    String role = auth.verify(parts[0], parts[1]);
                    if (role != null) { sendResponse(t, 200, "{\"success\":true,\"role\":\"" + role + "\"}"); return; }
                }
                sendResponse(t, 401, "{\"success\":false}");
            } else sendResponse(t, 405, "");
        }
    }

    static class RestockController extends BaseController {
        private InventoryService service;
        public RestockController(InventoryService s) { this.service = s; }
        @Override
        public void handle(HttpExchange t) throws IOException {
            String q = t.getRequestURI().getQuery();
            if (!"ADMIN".equals(t.getRequestHeaders().getFirst("Role"))) { sendResponse(t, 403, "{\"success\":false}"); return; }
            if (q != null) {
                Map<String, String> p = parseQuery(q);
                if (p.containsKey("id") && p.containsKey("qty")) {
                    int id = Integer.parseInt(p.get("id"));
                    double qty = Double.parseDouble(p.get("qty"));
                    if(service.restockItem(id, qty)) sendResponse(t, 200, "{\"success\":true}");
                    else sendResponse(t, 400, "{\"success\":false}");
                }
            }
        }
    }

    static class DataController extends BaseController {
        private InventoryService s;
        public DataController(InventoryService s) { this.s = s; }
        @Override
        public void handle(HttpExchange t) throws IOException { sendResponse(t, 200, s.getJsonData()); }
    }

    static class OrderController extends BaseController {
        private InventoryService s;
        public OrderController(InventoryService s) { this.s = s; }
        @Override
        public void handle(HttpExchange t) throws IOException {
            String q = t.getRequestURI().getQuery();
            int id = Integer.parseInt(q.split("=")[1]);
            sendResponse(t, 200, s.processOrder(id));
        }
    }

    static class StaticContentController extends BaseController {
        @Override
        public void handle(HttpExchange t) throws IOException {
            File f = new File("index.html");
            if (f.exists()) {
                t.sendResponseHeaders(200, f.length());
                Files.copy(f.toPath(), t.getResponseBody());
            } else t.sendResponseHeaders(404, 0);
            t.getResponseBody().close();
        }
    }
}
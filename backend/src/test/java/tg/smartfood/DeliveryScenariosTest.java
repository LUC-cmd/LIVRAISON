package tg.smartfood;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios de validation 1 a 12 du cahier des charges, joues de bout en bout sur l'API,
 * avec un vrai PostgreSQL et le coffre simule. Les tests s'enchainent : ils partagent le meme etat.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeliveryScenariosTest {

    private static final String API = "/api/v1";
    private static final String DEMO_PASSWORD = "Demo1234!";

    @TestConfiguration
    static class CleanDatabase {
        /** Base videe puis migree avant chaque execution de la suite. */
        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private String clientA;
    private String clientB;
    private String restaurantAfi;
    private String restaurantGrill;
    private String courier;
    private String admin;

    private long boxId;
    private long groupA;
    private long orderA;
    private long groupB;
    private long orderB;
    private int compartmentA;
    private int compartmentB;
    private long deliveryA;
    private long deliveryB;
    private String codeA;
    private String codeB;

    // ------------------------------------------------------------------ outils

    private JsonNode call(String method, String path, String token, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> MockMvcRequestBuilders.get(API + path);
            case "POST" -> MockMvcRequestBuilders.post(API + path);
            case "PUT" -> MockMvcRequestBuilders.put(API + path);
            default -> throw new IllegalArgumentException(method);
        };
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, response.getStatus(), method + " " + path + " -> " + content);
        return content.isBlank() ? json.createObjectNode() : json.readTree(content);
    }

    private JsonNode get(String path, String token) throws Exception {
        return call("GET", path, token, null, 200);
    }

    private JsonNode post(String path, String token, Object body) throws Exception {
        return call("POST", path, token, body, 200);
    }

    private String login(String email, String password) throws Exception {
        return call("POST", "/auth/login", null, Map.of("email", email, "password", password), 200).get("accessToken").asText();
    }

    private JsonNode find(JsonNode array, String field, Object value) {
        for (JsonNode node : array) {
            if (node.path(field).asText().equals(String.valueOf(value))) {
                return node;
            }
        }
        throw new AssertionError("Element introuvable : " + field + "=" + value + " dans " + array);
    }

    private JsonNode compartment(String token, int number) throws Exception {
        return find(get("/boxes/" + boxId, token).get("compartments"), "number", number);
    }

    private JsonNode hardware(int number) throws Exception {
        return find(get("/simulator/boxes/" + boxId, admin).get("compartments"), "number", number);
    }

    private String display() throws Exception {
        return get("/simulator/boxes/" + boxId, admin).get("displayText").asText();
    }

    private String keypad(String token, String code) throws Exception {
        return post("/simulator/boxes/" + boxId + "/keypad", token, Map.of("code", code)).get("displayText").asText();
    }

    private JsonNode orderOf(String token, long groupId, long orderId) throws Exception {
        return find(get("/customer/orders/" + groupId, token).get("orders"), "id", orderId);
    }

    private long dishId(String restaurantName, String dishName) throws Exception {
        JsonNode restaurant = find(get("/restaurants", null), "name", restaurantName);
        JsonNode detail = get("/restaurants/" + restaurant.get("id").asLong(), null);
        for (JsonNode category : detail.get("categories")) {
            for (JsonNode dish : category.get("dishes")) {
                if (dish.get("name").asText().equals(dishName)) {
                    return dish.get("id").asLong();
                }
            }
        }
        throw new AssertionError("Plat introuvable : " + dishName);
    }

    private JsonNode checkout(String token, long addressId, long dish, int quantity) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("paymentMethod", "CASH_ON_DELIVERY");
        body.put("items", List.of(Map.of("dishId", dish, "quantity", quantity)));
        return call("POST", "/customer/orders", token, body, 201);
    }

    /** Le restaurant accepte, prepare, obtient un compartiment, charge le repas et ferme la porte. Retourne le numero du compartiment. */
    private int prepareAndLoad(String restaurantToken, String customerToken, long groupId, long orderId) throws Exception {
        post("/restaurant/orders/" + orderId + "/accept", restaurantToken, null);
        JsonNode ready = post("/restaurant/orders/" + orderId + "/ready", restaurantToken, null);
        assertTrue(ready.get("compartmentReserved").asBoolean(), "Un compartiment doit etre reserve");
        JsonNode reserved = orderOf(customerToken, groupId, orderId);
        assertEquals("RESERVED", reserved.get("status").asText());
        int number = reserved.get("compartmentNumber").asInt();
        assertEquals("RESERVED", compartment(admin, number).get("state").asText());

        JsonNode opened = post("/restaurant/orders/" + orderId + "/loading-authorization", restaurantToken, null);
        assertEquals(number, opened.get("number").asInt(), "Seul le compartiment attribue s'ouvre");
        assertEquals("LOADING", compartment(admin, number).get("state").asText());
        assertTrue(hardware(number).get("doorOpen").asBoolean());

        post("/simulator/boxes/" + boxId + "/compartments/" + number + "/meal", restaurantToken, Map.of("present", true));
        post("/simulator/boxes/" + boxId + "/compartments/" + number + "/close", restaurantToken, null);
        JsonNode locked = compartment(admin, number);
        assertEquals("LOCKED", locked.get("state").asText(), "Fermeture detectee et verrouillage verifie");
        assertTrue(locked.get("locked").asBoolean());
        assertEquals("LOADED", orderOf(customerToken, groupId, orderId).get("status").asText());
        return number;
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    @Order(1)
    void scenario01_clientCreeSonCompteEtCommande() throws Exception {
        admin = login("admin@demo.tg", DEMO_PASSWORD);
        restaurantAfi = login("afi@demo.tg", DEMO_PASSWORD);
        restaurantGrill = login("edem@demo.tg", DEMO_PASSWORD);
        courier = login("koffi@demo.tg", DEMO_PASSWORD);
        clientB = login("kossi@demo.tg", DEMO_PASSWORD);
        boxId = get("/boxes", admin).get(0).get("id").asLong();
        assertEquals(6, get("/boxes/" + boxId, admin).get("compartments").size(), "Le coffre de reference a six compartiments");

        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put("fullName", "Client Test");
        registration.put("email", "client.test@smartfood.tg");
        registration.put("phone", "+22891112233");
        registration.put("password", "MotDePasse1!");
        registration.put("role", "CUSTOMER");
        clientA = call("POST", "/auth/register", null, registration, 201).get("accessToken").asText();
        call("POST", "/auth/register", null, registration, 409);
        registration.put("role", "ADMIN");
        registration.put("email", "pirate@smartfood.tg");
        registration.put("phone", "+22891112244");
        call("POST", "/auth/register", null, registration, 403);

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("label", "Maison");
        address.put("line", "Rue de l'Entente, Adidogome");
        address.put("city", "Lome");
        address.put("latitude", 6.1830);
        address.put("longitude", 1.1650);
        address.put("defaultAddress", true);
        long addressId = call("POST", "/me/addresses", clientA, address, 201).get("id").asLong();

        assertTrue(get("/restaurants", null).size() >= 3, "Les restaurants valides sont visibles sans connexion");
        JsonNode group = checkout(clientA, addressId, dishId("Chez Afi", "Ayimolou"), 2);
        groupA = group.get("id").asLong();
        orderA = group.get("orders").get(0).get("id").asLong();
        assertEquals(2000, group.get("subtotal").asInt(), "Le prix vient de la base : 2 x 1000 FCFA");
        assertEquals(2500, group.get("total").asInt());
        assertEquals("PENDING", group.get("orders").get(0).get("status").asText());
        assertEquals("PENDING", group.get("payment").get("status").asText());

        // Un panier vide ou une adresse d'un autre client est refuse.
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("addressId", addressId);
        empty.put("paymentMethod", "CASH_ON_DELIVERY");
        empty.put("items", List.of());
        call("POST", "/customer/orders", clientA, empty, 400);
        long otherAddress = get("/me/addresses", clientB).get(0).get("id").asLong();
        empty.put("addressId", otherAddress);
        empty.put("items", List.of(Map.of("dishId", dishId("Chez Afi", "Ayimolou"), "quantity", 1)));
        call("POST", "/customer/orders", clientA, empty, 400);
    }

    @Test
    @Order(2)
    void scenario02_et_03_restaurantPrepareChargeEtLeSystemeVerifieLaFermeture() throws Exception {
        JsonNode received = find(get("/restaurant/orders", restaurantAfi), "customerName", "Client Test");
        assertEquals(orderA, received.get("order").get("id").asLong());
        assertTrue(get("/notifications", restaurantAfi).get("unread").asInt() > 0, "Le restaurant recoit une notification");

        // Cloisonnement : un autre restaurant ne voit pas et ne peut pas traiter cette commande.
        assertEquals(0, get("/restaurant/orders", restaurantGrill).size());
        call("POST", "/restaurant/orders/" + orderA + "/accept", restaurantGrill, null, 404);
        // Pas d'ouverture avant la reservation d'un compartiment.
        call("POST", "/restaurant/orders/" + orderA + "/loading-authorization", restaurantAfi, null, 409);

        compartmentA = prepareAndLoad(restaurantAfi, clientA, groupA, orderA);
        // L'autorisation de chargement est a usage unique : le compartiment verrouille ne se rouvre pas.
        call("POST", "/restaurant/orders/" + orderA + "/loading-authorization", restaurantAfi, null, 409);
    }

    @Test
    @Order(3)
    void scenario04_codeUniqueReserveAuClient() throws Exception {
        JsonNode code = get("/customer/order-items/" + orderA + "/code", clientA);
        codeA = code.get("code").asText();
        assertTrue(codeA.matches("\\d{6}"), "Code numerique a six chiffres");
        assertEquals(compartmentA, code.get("compartmentNumber").asInt());
        assertTrue(orderOf(clientA, groupA, orderA).get("codeActive").asBoolean());

        // Le code n'est jamais stocke en clair.
        Map<String, Object> row = jdbc.queryForMap("select code_hash, code_encrypted from withdrawal_codes where order_id = ?", orderA);
        assertNotEquals(codeA, row.get("code_hash"));
        assertFalse(String.valueOf(row.get("code_encrypted")).contains(codeA));
        int leaks = jdbc.queryForObject("select count(*) from notifications where body like ?", Integer.class, "%" + codeA + "%");
        assertEquals(0, leaks, "Aucune notification ne contient le code");

        // Ni un autre client, ni le restaurant, ni le livreur, ni l'administrateur n'obtiennent le code.
        call("GET", "/customer/order-items/" + orderA + "/code", clientB, null, 404);
        call("GET", "/customer/order-items/" + orderA + "/code", restaurantAfi, null, 403);
        call("GET", "/customer/order-items/" + orderA + "/code", courier, null, 403);
        call("GET", "/customer/order-items/" + orderA + "/code", admin, null, 403);
        assertFalse(get("/restaurant/orders", restaurantAfi).toString().contains(codeA));
        assertFalse(get("/admin/orders", admin).toString().contains(codeA));
    }

    @Test
    @Order(4)
    void scenario05_et_06_livreurActiveSonGpsEtLeClientLeSuit() throws Exception {
        // Deuxieme client, deuxieme restaurant, deuxieme compartiment du meme coffre.
        long addressB = get("/me/addresses", clientB).get(0).get("id").asLong();
        JsonNode group = checkout(clientB, addressB, dishId("Grill du Boulevard", "Alloco"), 1);
        groupB = group.get("id").asLong();
        orderB = group.get("orders").get(0).get("id").asLong();
        compartmentB = prepareAndLoad(restaurantGrill, clientB, groupB, orderB);
        assertNotEquals(compartmentA, compartmentB, "Deux commandes actives n'occupent jamais le meme compartiment");
        codeB = get("/customer/order-items/" + orderB + "/code", clientB).get("code").asText();
        assertNotEquals(codeA, codeB);

        JsonNode missions = get("/courier/missions", courier);
        assertEquals(2, missions.size());
        assertFalse(missions.toString().contains(codeA), "La mission du livreur ne contient aucun code");
        assertFalse(missions.toString().contains(codeB));
        assertFalse(missions.toString().toLowerCase().contains("\"code\""));
        deliveryA = find(missions, "destinationAddress", "Rue de l'Entente, Adidogome, Lome").get("id").asLong();
        for (JsonNode mission : missions) {
            if (mission.get("id").asLong() != deliveryA) {
                deliveryB = mission.get("id").asLong();
            }
        }

        // Pas de position tant que la mission n'est pas acceptee, pas de depart sans prise en charge.
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("latitude", 6.1400);
        position.put("longitude", 1.2300);
        position.put("accuracyM", 12.0);
        position.put("simulated", true);
        call("POST", "/courier/missions/" + deliveryA + "/location", courier, position, 409);
        call("POST", "/courier/missions/" + deliveryA + "/depart", courier, null, 409);

        for (long delivery : new long[]{deliveryA, deliveryB}) {
            post("/courier/missions/" + delivery + "/accept", courier, null);
            post("/courier/missions/" + delivery + "/pickup", courier, null);
            post("/courier/missions/" + delivery + "/depart", courier, null);
        }
        assertEquals("IN_TRANSIT", orderOf(clientA, groupA, orderA).get("status").asText());

        JsonNode sent = post("/courier/missions/" + deliveryA + "/location", courier, position);
        assertTrue(sent.get("simulated").asBoolean(), "Une position simulee est identifiee comme telle");
        position.put("latitude", 6.1600);
        position.put("longitude", 1.2000);
        post("/courier/missions/" + deliveryA + "/location", courier, position);

        JsonNode tracking = get("/customer/orders/" + groupA + "/tracking", clientA).get(0);
        assertEquals("IN_TRANSIT", tracking.get("status").asText());
        assertEquals(6.1600, tracking.get("lastLocation").get("latitude").asDouble(), 1e-9);
        assertEquals(2, tracking.get("path").size());
        assertEquals("Koffi Amegan", tracking.get("courierName").asText());
        // Le suivi d'une commande est reserve a son client.
        call("GET", "/customer/orders/" + groupA + "/tracking", clientB, null, 404);

        for (long delivery : new long[]{deliveryA, deliveryB}) {
            post("/courier/missions/" + delivery + "/arrive", courier, null);
        }
        assertEquals("ARRIVED", orderOf(clientA, groupA, orderA).get("status").asText());
    }

    @Test
    @Order(5)
    void scenario09_leLivreurNePeutRienOuvrir() throws Exception {
        call("POST", "/restaurant/orders/" + orderA + "/loading-authorization", courier, null, 403);
        call("POST", "/admin/compartments/1/reset", courier, Map.of("mealDelivered", true), 403);
        call("PUT", "/simulator/admin/boxes/" + boxId + "/device", courier, Map.of("online", true), 403);
        String wrong = codeA.equals("000000") || codeB.equals("000000") ? "999999" : "000000";
        assertEquals("CODE INCORRECT", keypad(courier, wrong));
        assertEquals("LOCKED", compartment(admin, compartmentA).get("state").asText());
        assertEquals("LOCKED", compartment(admin, compartmentB).get("state").asText());
        assertFalse(hardware(compartmentA).get("doorOpen").asBoolean());
        // La vue du coffre pour le livreur ne revele pas les commandes.
        assertTrue(compartment(courier, compartmentA).get("orderId").isNull());
    }

    @Test
    @Order(6)
    void scenario07_et_12_leClientOuvreUniquementSonCompartiment() throws Exception {
        // Paiement a la livraison : pas d'ouverture avant l'encaissement confirme par le livreur.
        assertEquals("PAIEMENT REQUIS", keypad(clientA, codeA));
        assertEquals("LOCKED", compartment(admin, compartmentA).get("state").asText());
        post("/courier/missions/" + deliveryA + "/cash-received", courier, null);
        post("/courier/missions/" + deliveryB + "/cash-received", courier, null);

        assertEquals("COMPARTIMENT " + compartmentA + " OUVERT", keypad(clientA, codeA));
        assertEquals("OPEN", compartment(admin, compartmentA).get("state").asText());
        assertTrue(hardware(compartmentA).get("doorOpen").asBoolean());
        for (JsonNode other : get("/simulator/boxes/" + boxId, admin).get("compartments")) {
            if (other.get("number").asInt() != compartmentA) {
                assertFalse(other.get("doorOpen").asBoolean(), "Aucun autre compartiment ne s'ouvre");
            }
        }
        assertEquals("LOCKED", compartment(admin, compartmentB).get("state").asText());

        // Scenario 12 : retrait, fermeture, enregistrement, liberation apres verification.
        post("/simulator/boxes/" + boxId + "/compartments/" + compartmentA + "/meal", clientA, Map.of("present", false));
        post("/simulator/boxes/" + boxId + "/compartments/" + compartmentA + "/close", clientA, null);
        assertEquals("AVAILABLE", compartment(admin, compartmentA).get("state").asText());
        assertEquals("COLLECTED", orderOf(clientA, groupA, orderA).get("status").asText());
        assertEquals("USED", jdbc.queryForObject("select status from withdrawal_codes where order_id = ?", String.class, orderA));
        assertEquals("COMPLETED", find(get("/courier/missions", courier), "id", deliveryA).get("status").asText());

        // Le code est a usage unique.
        assertEquals("CODE INCORRECT", keypad(admin, codeA));
        assertEquals("AVAILABLE", compartment(admin, compartmentA).get("state").asText());

        String events = get("/admin/events/openings?boxId=" + boxId, admin).toString();
        for (String type : List.of("COMPARTMENT_RESERVED", "LOADING_OPENED", "LOADING_CLOSED", "LOCK_VERIFICATION", "CODE_GENERATED",
                "CODE_ACCEPTED", "WITHDRAWAL_OPENED", "WITHDRAWAL_CLOSED", "COMPARTMENT_RELEASED", "CODE_REJECTED")) {
            assertTrue(events.contains(type), "Evenement journalise : " + type);
        }
    }

    @Test
    @Order(7)
    void scenario10_codeExpireRefusePuisRenouvele() throws Exception {
        jdbc.update("update withdrawal_codes set expires_at = now() - interval '1 minute' where order_id = ? and status = 'ACTIVE'", orderB);
        assertEquals("CODE EXPIRE", keypad(clientB, codeB));
        assertEquals("LOCKED", compartment(admin, compartmentB).get("state").asText());
        assertFalse(hardware(compartmentB).get("doorOpen").asBoolean());
        call("GET", "/customer/order-items/" + orderB + "/code", clientB, null, 404);

        String renewed = post("/customer/order-items/" + orderB + "/code/renew", clientB, null).get("code").asText();
        assertNotEquals(codeB, renewed);
        assertEquals("CODE INCORRECT", keypad(clientB, codeB), "L'ancien code reste invalide");
        codeB = renewed;
    }

    @Test
    @Order(8)
    void scenario11_coffreHorsConnexion() throws Exception {
        call("PUT", "/simulator/admin/boxes/" + boxId + "/device", admin, Map.of("online", false), 200);
        assertFalse(get("/boxes/" + boxId, admin).get("device").get("online").asBoolean());
        // Procedure de securite : hors connexion, rien ne s'ouvre, meme avec un code valide.
        assertEquals("HORS LIGNE - REESSAYEZ", keypad(clientB, codeB));
        assertFalse(hardware(compartmentB).get("doorOpen").asBoolean());
        assertEquals("LOCKED", compartment(admin, compartmentB).get("state").asText());
        assertTrue(get("/notifications", admin).toString().contains("BOX_OFFLINE"), "L'incident est signale");
        assertTrue(get("/admin/events/sensors", admin).toString().contains("CONNECTION_LOST"));
        assertEquals(1, get("/admin/dashboard", admin).get("boxesOffline").asInt());

        call("PUT", "/simulator/admin/boxes/" + boxId + "/device", admin, Map.of("online", true), 200);
        assertTrue(get("/boxes/" + boxId, admin).get("device").get("online").asBoolean());
    }

    @Test
    @Order(9)
    void scenario08_unAutreClientOuvreUniquementLeSien() throws Exception {
        assertEquals("COMPARTIMENT " + compartmentB + " OUVERT", keypad(clientB, codeB));
        for (JsonNode other : get("/simulator/boxes/" + boxId, admin).get("compartments")) {
            assertEquals(other.get("number").asInt() == compartmentB, other.get("doorOpen").asBoolean());
        }
        post("/simulator/boxes/" + boxId + "/compartments/" + compartmentB + "/meal", clientB, Map.of("present", false));
        post("/simulator/boxes/" + boxId + "/compartments/" + compartmentB + "/close", clientB, null);
        assertEquals("AVAILABLE", compartment(admin, compartmentB).get("state").asText());
        assertEquals("COLLECTED", orderOf(clientB, groupB, orderB).get("status").asText());

        JsonNode dashboard = get("/admin/dashboard", admin);
        assertEquals(2, dashboard.get("ordersCollected").asInt());
        assertEquals(3000, dashboard.get("grossSales").asInt());
        assertEquals(6, dashboard.get("compartments").get("AVAILABLE").asInt());
        assertEquals(800, get("/courier/earnings", courier).get("total").asInt(), "Deux livraisons a 80 % de 500 FCFA");
    }

    @Test
    @Order(10)
    void pannes_tentativesExcessivesEtSerrureDefectueuse() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertEquals("CODE INCORRECT", keypad(admin, "12345" + i));
        }
        assertEquals("CLAVIER BLOQUE", keypad(admin, "123459"));
        assertEquals("CLAVIER BLOQUE", keypad(admin, "123458"));
        assertTrue(get("/admin/events/security", admin).toString().contains("KEYPAD_LOCKED"));
        post("/admin/boxes/" + boxId + "/unlock-keypad", admin, null);

        // Serrure qui ne se verrouille pas apres chargement : compartiment en panne, commande remise en attente.
        long addressA = get("/me/addresses", clientA).get(0).get("id").asLong();
        JsonNode group = checkout(clientA, addressA, dishId("Chez Afi", "Degue"), 1);
        long order = group.get("orders").get(0).get("id").asLong();
        long groupId = group.get("id").asLong();
        post("/restaurant/orders/" + order + "/accept", restaurantAfi, null);
        post("/restaurant/orders/" + order + "/ready", restaurantAfi, null);
        int number = orderOf(clientA, groupId, order).get("compartmentNumber").asInt();
        call("PUT", "/simulator/admin/boxes/" + boxId + "/compartments/" + number + "/fault", admin, Map.of("fault", "LOCK_BROKEN"), 200);
        post("/restaurant/orders/" + order + "/loading-authorization", restaurantAfi, null);
        post("/simulator/boxes/" + boxId + "/compartments/" + number + "/close", restaurantAfi, null);
        assertEquals("FAULT", compartment(admin, number).get("state").asText());
        assertEquals("READY", orderOf(clientA, groupId, order).get("status").asText());
        assertFalse(orderOf(clientA, groupId, order).get("codeActive").asBoolean(), "Pas de code sans verrouillage confirme");

        // Remise en service refusee tant que la serrure n'est pas enclenchee, acceptee apres reparation.
        call("POST", "/admin/compartments/" + compartment(admin, number).get("id").asLong() + "/reset", admin, Map.of("mealDelivered", false), 409);
        call("PUT", "/simulator/admin/boxes/" + boxId + "/compartments/" + number + "/fault", admin, Map.of("fault", "NONE"), 200);
        post("/simulator/admin/boxes/" + boxId + "/compartments/" + number + "/force-open", admin, null);
        post("/simulator/boxes/" + boxId + "/compartments/" + number + "/close", admin, null);
        post("/admin/compartments/" + compartment(admin, number).get("id").asLong() + "/reset", admin, Map.of("mealDelivered", false));
        assertEquals("AVAILABLE", compartment(admin, number).get("state").asText());

        // La commande retrouve un compartiment sain.
        assertTrue(post("/restaurant/orders/" + order + "/assign-compartment", restaurantAfi, null).get("compartmentReserved").asBoolean());
    }
}

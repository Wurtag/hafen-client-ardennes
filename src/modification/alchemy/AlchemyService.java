package modification.alchemy;

import haven.ItemInfo;
import haven.Resource;
import haven.res.ui.tt.q.qbuff.QBuff;
import haven.resutil.FoodInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AlchemyService {
    public static final String API_ENDPOINT = "https://raw.githubusercontent.com/Cediner/ArdClient/Unstable/";
    private static final String ALCHEMY_DATA_URL = "https://raw.githubusercontent.com/Cediner/ArdClient/Unstable/data/alchemy-info.json";
    private static final File ALCHEMY_DATA_CACHE_FILE = new File("alchemy_data.json");

    private static final Map<String, ParsedAlchemyInfo> cachedItems = new ConcurrentHashMap<>();
    private static final Queue<HashedAlchemyInfo> sendQueue = new ConcurrentLinkedQueue<>();
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    static {
        if (!Resource.language.equals("en")) {
            System.out.println("AlchemyUtil ERROR: Only English language is allowed to send alchemy data");
        }
        scheduler.execute(AlchemyService::loadCachedAlchemyData);
        scheduler.scheduleAtFixedRate(AlchemyService::sendItems, 10L, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(AlchemyService::requestAlchemyDataCache, 0L, 30, TimeUnit.MINUTES);
    }

    /**
     * Load cached alchemy data from the file (only keys for now since we don't use content anyway)
     */
    private static void loadCachedAlchemyData() {
        try {
            if (ALCHEMY_DATA_CACHE_FILE.exists()) {
                String jsonData = String.join("", Files.readAllLines(ALCHEMY_DATA_CACHE_FILE.toPath(), StandardCharsets.UTF_8));
                JSONObject object = new JSONObject(jsonData);
                object.keySet().forEach(key -> cachedItems.put(key, new ParsedAlchemyInfo()));
                System.out.println("Loaded alchemy data file: " + cachedItems.size() + " entries");
            }
        } catch (Exception ex) {
            System.err.println("Cannot load alchemy data file: " + ex.getMessage());
        }
    }

    /**
     * Check last modified for the alchemy_data file and request update from server if too old
     */
    public static void requestAlchemyDataCache() {
        try {
            long lastModified = 0;
            if (ALCHEMY_DATA_CACHE_FILE.exists()) {
                lastModified = ALCHEMY_DATA_CACHE_FILE.lastModified();
            }
            if (System.currentTimeMillis() - lastModified > TimeUnit.MINUTES.toMillis(30)) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(ALCHEMY_DATA_URL).openConnection();
                    connection.setRequestProperty("User-Agent", "H&H Client");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    StringBuilder stringBuilder = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        stringBuilder.append(reader.readLine());
                    } finally {
                        connection.disconnect();
                    }
                    String content = stringBuilder.toString();

                    Files.write(ALCHEMY_DATA_CACHE_FILE.toPath(), Collections.singleton(content), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                    JSONObject object = new JSONObject(content);
                    object.keySet().forEach(key -> cachedItems.put(key, new ParsedAlchemyInfo()));
                    System.out.println("Updated alchemy data file: " + cachedItems.size() + " entries");
                } catch (Exception ex) {
                    System.err.println("Cannot load remote alchemy data file: " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            System.out.println("Should not happen, but whatever: " + ex.getMessage());
        }
    }

    /**
     * Check item info and determine if it is alchemy and we need to send it
     */
    public static void checkAlchemy(List<ItemInfo> infoList, String resName) {
        if (!Resource.language.equals("en")) {
            // Do not process localized items
            return;
        }
        try {
            FoodInfo alchemyInfo = ItemInfo.find(FoodInfo.class, infoList);
            if (alchemyInfo != null) {
                QBuff qBuff = ItemInfo.find(QBuff.class, infoList);
                double quality = qBuff != null ? qBuff.q : 10.0;
                double multiplier = Math.sqrt(quality / 10.0);

                ParsedAlchemyInfo parsedAlchemyInfo = new ParsedAlchemyInfo(); //FIXME look at
                parsedAlchemyInfo.resourceName = resName;
                parsedAlchemyInfo.energy = (int) (Math.round(alchemyInfo.end * 100));
                parsedAlchemyInfo.hunger = round2Dig(alchemyInfo.glut * 100);

                for (int i = 0; i < alchemyInfo.evs.length; i++) {
                    parsedAlchemyInfo.feps.add(new FoodFEP(alchemyInfo.evs[i].ev.orignm, round2Dig(alchemyInfo.evs[i].a / multiplier)));
                }

                for (ItemInfo info : infoList) {
                    if (info instanceof ItemInfo.AdHoc) {
                        String text = ((ItemInfo.AdHoc) info).str.text;
                        // Skip alchemy which base FEP's cannot be calculated
                        if (text.equals("White-truffled")
                                || text.equals("Black-truffled")
                                || text.equals("Peppered")) {
                            return;
                        }
                    }
                    if (info instanceof ItemInfo.Name) {
                        parsedAlchemyInfo.itemName = ((ItemInfo.Name) info).str.text;
                    }
                    if (info.getClass().getName().equals("Ingredient")) {
                        String name = (String) info.getClass().getField("name").get(info);
                        Double value = (Double) info.getClass().getField("val").get(info);
                        parsedAlchemyInfo.ingredients.add(new AlchemyIngredient(name, (int) (value * 100)));
                    } else if (info.getClass().getName().equals("Smoke")) {
                        String name = (String) info.getClass().getField("name").get(info);
                        Double value = (Double) info.getClass().getField("val").get(info);
                        parsedAlchemyInfo.ingredients.add(new AlchemyIngredient(name, (int) (value * 100)));
                    }
                }

                checkAndSend(parsedAlchemyInfo);
            }
        } catch (Exception ex) {
            System.out.println("Cannot create alchemy info: " + ex.getMessage());
        }
    }

    private static double round2Dig(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static void checkAndSend(ParsedAlchemyInfo info) {
        String hash = generateHash(info);
        if (cachedItems.containsKey(hash)) {
            return;
        }
        sendQueue.add(new HashedAlchemyInfo(hash, info));
    }

    private static void sendItems() {
        if (sendQueue.isEmpty()) {
            return;
        }

        List<ParsedAlchemyInfo> toSend = new ArrayList<>();
        while (!sendQueue.isEmpty()) {
            HashedAlchemyInfo info = sendQueue.poll();
            if (cachedItems.containsKey(info.hash)) {
                continue;
            }
            cachedItems.put(info.hash, info.alchemyInfo);
            toSend.add(info.alchemyInfo);
        }

        if (!toSend.isEmpty()) {
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(API_ENDPOINT + "alchemy").openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "H&H Client");
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(new JSONArray(toSend.toArray()).toString().getBytes(StandardCharsets.UTF_8));
                }
                StringBuilder stringBuilder = new StringBuilder();
                try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    stringBuilder.append(inputStream.readLine());
                }

                int code = connection.getResponseCode();
                if (code != 200) {
                    System.out.println("Response: " + stringBuilder.toString());
                }
                System.out.println("Sent " + toSend.size() + " alchemy items, code: " + code);
            } catch (Exception ex) {
                System.out.println("Cannot send " + toSend.size() + " alchemy items, error: " + ex.getMessage());
            }
        }
    }

    private static String generateHash(ParsedAlchemyInfo alchemyInfo) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(alchemyInfo.itemName).append(";")
                    .append(alchemyInfo.resourceName).append(";");
            alchemyInfo.ingredients.forEach(it -> {
                stringBuilder.append(it.name).append(";").append(it.percentage).append(";");
            });

            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
            return getHex(hash);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Cannot generate alchemy hash");
        }
        return null;
    }

    private static String getHex(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(1, bytes);
        return bigInteger.toString(16);
    }

    private static class HashedAlchemyInfo {
        public String hash;
        public ParsedAlchemyInfo alchemyInfo;

        public HashedAlchemyInfo(String hash, ParsedAlchemyInfo alchemyInfo) {
            this.hash = hash;
            this.alchemyInfo = alchemyInfo;
        }
    }

    public static class AlchemyIngredient {
        private String name;
        private Integer percentage;

        public AlchemyIngredient(String name, Integer percentage) {
            this.name = name;
            this.percentage = percentage;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, percentage);
        }

        public Integer getPercentage() {
            return percentage;
        }

        public String getName() {
            return name;
        }
    }

    public static class FoodFEP {
        private String name;
        private Double value;

        public FoodFEP(String name, Double value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        public String getName() {
            return name;
        }

        public Double getValue() {
            return value;
        }
    }

    public static class ParsedAlchemyInfo {
        public String itemName;
        public String resourceName;
        public Integer energy;
        public double hunger;
        public ArrayList<AlchemyIngredient> ingredients;
        public ArrayList<FoodFEP> feps;

        public ParsedAlchemyInfo() {
            this.itemName = "";
            this.resourceName = "";
            this.ingredients = new ArrayList<>();
            this.feps = new ArrayList<>();
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemName, resourceName, ingredients);
        }

        public ArrayList<FoodFEP> getFeps() {
            return feps;
        }

        public ArrayList<AlchemyIngredient> getIngredients() {
            return ingredients;
        }

        public String getItemName() {
            return itemName;
        }

        public String getResourceName() {
            return resourceName;
        }

        public double getHunger() {
            return hunger;
        }

        public Integer getEnergy() {
            return energy;
        }
    }
}

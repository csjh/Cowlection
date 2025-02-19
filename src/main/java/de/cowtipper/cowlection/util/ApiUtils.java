package de.cowtipper.cowlection.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.util.UUIDTypeAdapter;
import de.cowtipper.cowlection.Cowlection;
import de.cowtipper.cowlection.chesttracker.HyBazaarData;
import de.cowtipper.cowlection.chesttracker.LowestBinsCache;
import de.cowtipper.cowlection.command.exception.ThrowingConsumer;
import de.cowtipper.cowlection.config.CredentialStorage;
import de.cowtipper.cowlection.data.*;
import de.cowtipper.cowlection.error.ApiAskPolitelyErrorEvent;
import de.cowtipper.cowlection.error.ApiHttpErrorEvent;
import de.cowtipper.cowlection.error.ApiHttpErrorException;
import net.minecraftforge.common.MinecraftForge;
import org.apache.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiUtils {
    public static final String UUID_NOT_FOUND = "UUID-NOT-FOUND";
    private static final String NAME_TO_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_TO_NAME_URL = "https://api.mojang.com/user/profiles/%s/names";
    private static final String ONLINE_STATUS_URL = "https://api.hypixel.net/status?key=%s&uuid=%s";
    private static final String SKYBLOCK_STATS_URL = "https://api.hypixel.net/skyblock/profiles?key=%s&uuid=%s";
    private static final String BAZAAR_URL = "https://api.hypixel.net/skyblock/bazaar";
    public static final String LOWEST_BINS = "https://moulberry.codes/lowestbin.json";
    private static final String PLAYER_URL = "https://api.hypixel.net/player?key=%s&uuid=%s";
    private static final String API_KEY_URL = "https://api.hypixel.net/key?key=%s";
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    private ApiUtils() {
    }

    public static void fetchFriendData(String name, ThrowingConsumer<Friend> action) {
        pool.execute(() -> action.accept(getFriend(name)));
    }

    private static Friend getFriend(String name) {
        try (BufferedReader reader = makeApiCall(NAME_TO_UUID_URL + name)) {
            if (reader == null) {
                return Friend.FRIEND_NOT_FOUND;
            } else {
                return GsonUtils.fromJson(reader, Friend.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    public static void fetchCurrentName(Friend friend, ThrowingConsumer<String> action) {
        pool.execute(() -> action.accept(getCurrentName(friend)));
    }

    private static String getCurrentName(Friend friend) {
        try (BufferedReader reader = makeApiCall(String.format(UUID_TO_NAME_URL, UUIDTypeAdapter.fromUUID(friend.getUuid())))) {
            if (reader == null) {
                return UUID_NOT_FOUND;
            } else {
                JsonArray nameHistoryData = new JsonParser().parse(reader).getAsJsonArray();
                if (nameHistoryData.size() > 0) {
                    return nameHistoryData.get(nameHistoryData.size() - 1).getAsJsonObject().get("name").getAsString();
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    public static void fetchPlayerStatus(Friend friend, ThrowingConsumer<HyStalkingData> action) {
        pool.execute(() -> action.accept(stalkPlayer(friend)));
    }

    private static HyStalkingData stalkPlayer(Friend friend) {
        try (BufferedReader reader = makeApiCall(String.format(ONLINE_STATUS_URL, CredentialStorage.moo, UUIDTypeAdapter.fromUUID(friend.getUuid())))) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, HyStalkingData.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    public static void fetchSkyBlockStats(Friend friend, ThrowingConsumer<HySkyBlockStats> action) {
        pool.execute(() -> action.accept(stalkSkyBlockStats(friend)));
    }

    private static HySkyBlockStats stalkSkyBlockStats(Friend friend) {
        try (BufferedReader reader = makeApiCall(String.format(SKYBLOCK_STATS_URL, CredentialStorage.moo, UUIDTypeAdapter.fromUUID(friend.getUuid())))) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, HySkyBlockStats.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    public static void fetchBazaarData(ThrowingConsumer<HyBazaarData> action) {
        pool.execute(() -> action.accept(getBazaarData()));
    }

    private static HyBazaarData getBazaarData() {
        try (BufferedReader reader = makeApiCall(BAZAAR_URL)) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, HyBazaarData.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    public static void fetchLowestBins(ThrowingConsumer<LowestBinsCache> action) {
        pool.execute(() -> action.accept(getLowestBins()));
    }

    private static LowestBinsCache getLowestBins() {
        try (BufferedReader reader = makeApiCall(LOWEST_BINS)) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, LowestBinsCache.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return new LowestBinsCache();
    }

    public static void fetchHyPlayerDetails(Friend stalkedPlayer, ThrowingConsumer<HyPlayerData> action) {
        pool.execute(() -> action.accept(stalkHyPlayer(stalkedPlayer)));
    }

    private static HyPlayerData stalkHyPlayer(Friend stalkedPlayer) {
        try (BufferedReader reader = makeApiCall(String.format(PLAYER_URL, CredentialStorage.moo, UUIDTypeAdapter.fromUUID(stalkedPlayer.getUuid())))) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, HyPlayerData.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            ApiAskPolitelyErrorEvent event = new ApiAskPolitelyErrorEvent(stalkedPlayer.getName());
            MinecraftForge.EVENT_BUS.post(event);
            handleApiException(e);
        }
        return null;
    }

    public static void fetchApiKeyInfo(String moo, ThrowingConsumer<HyApiKey> action) {
        pool.execute(() -> action.accept(getApiKeyInfo(moo)));
    }

    private static HyApiKey getApiKeyInfo(String moo) {
        try (BufferedReader reader = makeApiCall(String.format(API_KEY_URL, moo))) {
            if (reader != null) {
                return GsonUtils.fromJson(reader, HyApiKey.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            handleApiException(e);
        }
        return null;
    }

    private static void handleApiException(Exception e) {
        e.printStackTrace();
        if (e instanceof ApiHttpErrorException) {
            MinecraftForge.EVENT_BUS.post(new ApiHttpErrorEvent(e.getMessage(), ((ApiHttpErrorException) e).getUrl()));
        }
    }

    private static BufferedReader makeApiCall(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.addRequestProperty("User-Agent", "Forge Mod " + Cowlection.MODNAME + "/" + Cowlection.VERSION + " (" + Cowlection.GITURL + ")");

        connection.getResponseCode();
        if (connection.getResponseCode() == HttpStatus.SC_NO_CONTENT) { // http status 204
            return null;
        } else if (connection.getResponseCode() == HttpStatus.SC_BAD_GATEWAY && url.startsWith("https://api.hypixel.net/")) { // http status 502 (cloudflare)
            throw new ApiHttpErrorException("Couldn't contact Hypixel API (502 Bad Gateway). API might be down, check https://status.hypixel.net for info.", "https://status.hypixel.net");
        } else if (connection.getResponseCode() == HttpStatus.SC_SERVICE_UNAVAILABLE) { // http status 503 Service Unavailable
            int queryParamStart = url.indexOf('?', 10);
            String baseUrl = queryParamStart > 0 ? url.substring(0, queryParamStart) : url;
            throw new ApiHttpErrorException("Couldn't contact the API (503 Service unavailable). API might be down, or you might be blocked by Cloudflare, check if you can reach: " + baseUrl, url);
        } else if (connection.getResponseCode() == HttpStatus.SC_BAD_GATEWAY && url.startsWith("https://moulberry.codes/")) { // http status 502 (cloudflare)
            throw new ApiHttpErrorException("Couldn't contact Moulberry's API (502 Bad Gateway). API might be down, check if " + LOWEST_BINS + " is reachable.", LOWEST_BINS);
        } else {
            BufferedReader reader;
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                reader = new BufferedReader(new InputStreamReader(errorStream));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            }
            return reader;
        }
    }
}

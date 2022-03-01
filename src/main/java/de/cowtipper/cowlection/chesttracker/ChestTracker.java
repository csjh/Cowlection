package de.cowtipper.cowlection.chesttracker;

import com.google.gson.reflect.TypeToken;
import de.cowtipper.cowlection.Cowlection;
import de.cowtipper.cowlection.config.ChestTrackerStorage;
import de.cowtipper.cowlection.data.DataHelper;
import de.cowtipper.cowlection.data.HySkyBlockStats;
import de.cowtipper.cowlection.util.ApiUtils;
import de.cowtipper.cowlection.util.GsonUtils;
import de.cowtipper.cowlection.util.MooChatComponent;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ChestTracker {
    public static long lastBazaarUpdate;
    public static long lastLowestBinsUpdate;
    private final Map<BlockPos, List<ItemStack>> chestCache = new HashMap<>();
    private final Map<BlockPos, EnumFacing> doubleChestCache = new HashMap<>();
    private final Set<BlockPos> chestsWithWantedItem = new HashSet<>();
    private Map<String, ItemData> analysisResult = new HashMap<>();
    private ChestInteractionListener chestInteractionListener;
    private HyBazaarData bazaarCache;
    private LowestBinsCache lowestBinsCache;
    private final Cowlection main;
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    public ChestTracker(Cowlection main) {
        this.main = main;

        if (!ChestTrackerStorage.stringDoubleChestCache.isEmpty()) {
            Type collectionType = new TypeToken<Map<Long, String>>() {}.getType();
            Map<Long, String> tempDoubleChestCache = GsonUtils.fromJson(ChestTrackerStorage.stringDoubleChestCache, collectionType);
            for (Map.Entry<Long, String> entry : tempDoubleChestCache.entrySet()) {
                this.doubleChestCache.put(BlockPos.fromLong(entry.getKey()), EnumFacing.byName(entry.getValue()));
            }
        }

        if (!ChestTrackerStorage.stringChestCache.isEmpty()) {
            Type collectionType = new TypeToken<Map<Long, List<String>>>() {}.getType();
            Map<Long, List<String>> tempChestCache = GsonUtils.fromJson(ChestTrackerStorage.stringChestCache, collectionType);
            for (Map.Entry<Long, List<String>> entry : tempChestCache.entrySet()) {
                List<ItemStack> tempStack = new ArrayList<>();
                for (String str : entry.getValue()) {
                    try {
                        tempStack.add(ItemStack.loadItemStackFromNBT(JsonToNBT.getTagFromJson(str)));
                    } catch (NBTException e) {
                        e.printStackTrace();
                    }
                }
                this.chestCache.put(BlockPos.fromLong(entry.getKey()), tempStack);
            }
        }

        refreshPriceCache();
        chestInteractionListener = new ChestInteractionListener(main);
        MinecraftForge.EVENT_BUS.register(chestInteractionListener);
    }

    public void analyzeResults() {
        Map<String, ItemData> itemCounts = new HashMap<>();
        List<String> ChestItemNBT = new ArrayList<>();

        for (List<ItemStack> chestContents : chestCache.values()) {
            for (ItemStack item : chestContents) {
                NBTTagCompound nbt = item.serializeNBT();
                ChestItemNBT.add(GsonUtils.toJson(nbt));
//                String result = nbt.toString().replace("[", "{").replace("]", "}").replaceAll("[0-9]+ bytes", "");
//                try {
//                    Map map = GsonUtils.fromJson(result, Map.class);
//                    ChestItemNBT.add(new Gson().toJson(map));
//                } catch (JsonSyntaxException e) {
//                    System.out.println(nbt);
//                    e.printStackTrace();
//                }

                String key = item.hasDisplayName() ? item.getDisplayName() : item.getUnlocalizedName();

                boolean isAmbiguousItem = false;
                if (item.hasTagCompound()) {
                    key = item.getTagCompound().getCompoundTag("ExtraAttributes").getString("id");
                }
                if ("PET".equals(key)) {
                    HySkyBlockStats.Profile.Pet petInfo = GsonUtils.fromJson(item.getTagCompound().getCompoundTag("ExtraAttributes").getString("petInfo"), HySkyBlockStats.Profile.Pet.class);
                    key = petInfo.getType() + ";" + petInfo.getRarity().ordinal();
                    // remove pet lvl from name, as lowest BINs also disregard it
                    String petName = item.getDisplayName();
                    int endOfPetLevel = petName.indexOf(']');
                    if (petName.startsWith(EnumChatFormatting.GRAY + "[Lvl ") && endOfPetLevel > 0) {
                        item.setStackDisplayName(EnumChatFormatting.GRAY + "[Lvl " + EnumChatFormatting.DARK_GRAY + "?" + EnumChatFormatting.GRAY + petName.substring(endOfPetLevel));
                    }
                } else if (DataHelper.AMBIGUOUS_ITEM_IDS.contains(key)) {
                    isAmbiguousItem = true;
                    key += "_ambiguous";
                }

                ItemData itemData = itemCounts.get(key);
                if (itemData == null) {
                    // item hasn't been cached yet
                    if (isAmbiguousItem) {
                        convertToDummyItem(item, key);
                    }
                    itemData = new ItemData(key, item.copy());
                }
                itemCounts.put(key, itemData.addAmount(item.stackSize));
            }
        }
        this.analysisResult = itemCounts;
    }

    private void convertToDummyItem(ItemStack item, String key) {
        NBTTagCompound itemNbtDisplay = item.getSubCompound("display", true);
        NBTTagList loreList = new NBTTagList();
        loreList.appendTag(new NBTTagString("" + EnumChatFormatting.RED + EnumChatFormatting.ITALIC + "This ambiguous item type"));
        loreList.appendTag(new NBTTagString("" + EnumChatFormatting.RED + EnumChatFormatting.ITALIC + "is not listed separately."));
        itemNbtDisplay.setTag("Lore", loreList);
        String itemName = null;
        switch (key) {
            case "ENCHANTED_BOOK_ambiguous":
                itemName = "Enchanted Book";
                break;
            case "POTION_ambiguous":
                itemName = "Potion";
                break;
            case "RUNE_ambiguous":
                itemName = "Rune";
                NBTTagCompound skullNbtTextureData = item.getSubCompound("SkullOwner", false);
                if (skullNbtTextureData != null) {
                    skullNbtTextureData.setString("Id", UUID.randomUUID().toString());
                    NBTTagCompound nbtSkin = new NBTTagCompound();
                    // set texture to Empty Rune
                    nbtSkin.setString("Value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODJiODIwN2E1ZmUxOTJjZDQ3N2U5MjE0NjYxOTdjOGFmNzQ5YWYxOGRkMWVmMzg5ZTI3MzNhMmY3NGQwOTI4YiJ9fX0=");
                    skullNbtTextureData.getCompoundTag("Properties").getTagList("textures", Constants.NBT.TAG_COMPOUND).set(0, nbtSkin);
                }
                break;
            case "NEW_YEAR_CAKE_ambiguous":
                itemName = "New Year Cake";
                break;
            case "SPOOKY_PIE_ambiguous":
                itemName = "Spooky Pie";
                break;
            case "CAKE_SOUL_ambiguous":
                itemName = "Cake Soul";
                break;
        }
        if (itemName != null) {
            item.setStackDisplayName(EnumChatFormatting.GRAY + itemName);
        }
    }

    /**
     * Returns ordered analysis result with prices
     */
    public List<ItemData> getAnalysisResult(ChestOverviewGui.Column orderBy, boolean orderDesc, boolean useInstantSellPrices) {
        List<ItemData> orderedAnalysisResult = new ArrayList<>();
        // sort by bazaar value (most value first)
        for (Map.Entry<String, ItemData> itemEntry : analysisResult.entrySet()) {
            boolean foundPriceForItem = false;
            if (bazaarCache != null && bazaarCache.isSuccess()) {
                String productKey = itemEntry.getKey();
                HyBazaarData.Product product = bazaarCache.getProduct(productKey);
                if (product != null) {
                    // item is sold on bazaar!
                    itemEntry.getValue().setBazaarInstantSellPrice(product.getInstantSellPrice());
                    itemEntry.getValue().setBazaarSellOfferPrice(product.getSellOfferPrice());
                    foundPriceForItem = true;
                }
            }
            if (!foundPriceForItem && lowestBinsCache != null && lowestBinsCache.size() > 0) {
                String productKey = itemEntry.getKey().replace(':', '-');
                Integer lowestBin = lowestBinsCache.get(productKey);
                if (lowestBin != null) {
                    // item is sold via BIN
                    itemEntry.getValue().setLowestBin(lowestBin);
                }
            }
            orderedAnalysisResult.add(itemEntry.getValue());
        }
        Comparator<ItemData> comparator;
        switch (orderBy) {
            case ITEM_NAME:
                comparator = Comparator.comparing(ItemData::getName);
                break;
            case ITEM_AMOUNT:
                comparator = Comparator.comparing(ItemData::getAmount);
                break;
            case PRICE_EACH:
                comparator = Comparator.comparing(itemData -> itemData.getPrice(useInstantSellPrices));
                break;
            default: // case PRICE_SUM:
                comparator = Comparator.comparing(itemData -> itemData.getPriceSum(useInstantSellPrices));
                break;
        }
        orderedAnalysisResult.sort((orderDesc ? comparator.reversed() : comparator).thenComparing(ItemData::getName));
        return orderedAnalysisResult;
    }

    public Set<BlockPos> getCachedPositions() {
        return chestCache.keySet();
    }

    public Set<BlockPos> getChestsWithWantedItem() {
        return chestsWithWantedItem;
    }

    public void clear() {
        MinecraftForge.EVENT_BUS.unregister(chestInteractionListener);
        chestInteractionListener = null;
        bazaarCache = null;
        lowestBinsCache = null;
        chestCache.clear();
        doubleChestCache.clear();
        chestsWithWantedItem.clear();
        analysisResult.clear();
    }

    public void addChest(BlockPos chestPos, List<ItemStack> chestContents, EnumFacing otherChestFacing) {
        if (chestContents.size() > 0) { // check if the chest is a chest we want to cache/analyze
            ItemStack firstItem = chestContents.get(0);
            if (firstItem != null && firstItem.hasDisplayName() && firstItem.getDisplayName().equals(" ") && firstItem.getItem() == Item.getItemFromBlock(Blocks.stained_glass_pane)) {
                // item in first slot of chest is a glass pane with the display name " ", indicating e.g. a minion chest which we don't want to track
                return;
            }
        }
        BlockPos mainChestPos = chestPos;

        if (otherChestFacing != EnumFacing.UP) { // we have a double chest!
            if (isOtherChestCached(chestPos, otherChestFacing)) { // other chest is cached already, update that one instead
                mainChestPos = chestPos.offset(otherChestFacing);
            }

            if (chestPos.equals(mainChestPos)) {
                doubleChestCache.put(chestPos, otherChestFacing);
            } else {
                doubleChestCache.put(mainChestPos, otherChestFacing.getOpposite());
            }
            pool.execute(this::buildDoubleCache);
        }
        chestCache.put(mainChestPos, chestContents);
        pool.execute(this::buildCache);
    }

    public void removeChest(BlockPos chestPos, EnumFacing otherChestFacing) {
        BlockPos mainChestPos = chestPos;

        if (otherChestFacing != EnumFacing.UP) { // we have a double chest!
            if (isOtherChestCached(chestPos, otherChestFacing)) { // other chest is cached already, update that one instead
                mainChestPos = chestPos.offset(otherChestFacing);
            }

            if (chestPos.equals(mainChestPos)) {
                doubleChestCache.remove(chestPos);
            } else {
                doubleChestCache.remove(mainChestPos);
            }
            pool.execute(this::buildDoubleCache);
        }
        chestCache.remove(mainChestPos);
        chestsWithWantedItem.remove(mainChestPos);
        pool.execute(this::buildCache);
    }

    private void buildDoubleCache() {
        Map<Long, String> doubleCacheStringBuilder = new HashMap<>();
        for (Map.Entry<BlockPos, EnumFacing> entry : doubleChestCache.entrySet()) {
            doubleCacheStringBuilder.put(entry.getKey().toLong(), entry.getValue().getName());
        }
        Cowlection.getInstance().getChestStorage().setDoubleChestCache(GsonUtils.toJson(doubleCacheStringBuilder));
    }

    private void buildCache() {
        Map<Long, List<String>> cacheStringBuilder = new HashMap<>();
        for (Map.Entry<BlockPos, List<ItemStack>> entry : chestCache.entrySet()) {
            cacheStringBuilder.put(
                    entry.getKey().toLong(),
                    entry.getValue().stream().map(
                            x -> x.serializeNBT().toString()
                    ).collect(Collectors.toList())
            );
        }
        Cowlection.getInstance().getChestStorage().setChestCache(GsonUtils.toJson(cacheStringBuilder));
//        StringBuilder sb = new StringBuilder();
//        sb.append('{');
//        for (Map.Entry<BlockPos, List<ItemStack>> entry : chestCache.entrySet()) {
//            if (sb.length() != 1) sb.append(',');
//            sb.append(entry.getKey().toLong());
//            sb.append(":[");
//            for (ItemStack stack : entry.getValue()) sb.append(stack.serializeNBT());
//            sb.append(']');
//        }
//        sb.deleteCharAt(sb.length()-1);
//        sb.append('}');
    }

    private boolean isOtherChestCached(BlockPos chestPos, EnumFacing otherChestFacing) {
        BlockPos otherChestPos = chestPos.offset(otherChestFacing);
        return chestCache.containsKey(otherChestPos);
    }

    public EnumFacing getOtherChestFacing(BlockPos pos) {
        return doubleChestCache.getOrDefault(pos, EnumFacing.UP);
    }

    public EnumSet<Updating> refreshPriceCache() {
        EnumSet<Updating> updating = EnumSet.of(Updating.UNDEFINED);
        if (allowUpdateBazaar()) {
            updating.add(Updating.BAZAAR);
            ApiUtils.fetchBazaarData(bazaarData -> {
                if (bazaarData == null || !bazaarData.isSuccess()) {
                    main.getChatHelper().sendMessage(new MooChatComponent("Error: Couldn't get Bazaar data from Hypixel API! API might be down: check status.hypixel.net").red().setUrl("https://status.hypixel.net/"));
                }
                this.bazaarCache = bazaarData;
                lastBazaarUpdate = System.currentTimeMillis();
            });
        }
        if (allowUpdateLowestBins()) {
            updating.add(Updating.LOWEST_BINS);
            ApiUtils.fetchLowestBins(lowestBins -> {
                if (!lowestBins.hasData()) {
                    main.getChatHelper().sendMessage(new MooChatComponent("Error: Couldn't get lowest BINs from Moulberry's API! API might be down: check if " + ApiUtils.LOWEST_BINS + " is reachable.").red().setUrl(ApiUtils.LOWEST_BINS));
                }
                this.lowestBinsCache = lowestBins;
                lastLowestBinsUpdate = System.currentTimeMillis();
            });
        }
        return updating;
    }

    /**
     * Allow bazaar update once per minute
     */
    public boolean allowUpdateBazaar() {
        return bazaarCache == null || bazaarCache.allowRefreshData();
    }

    /**
     * Allow lowest bins update once every 5 minutes
     */
    public boolean allowUpdateLowestBins() {
        return lowestBinsCache == null || (System.currentTimeMillis() - lastLowestBinsUpdate) > 300000;
    }

    public void markChestsWithWantedItem(String sbKey, int amount, String itemName) {
        // clear old search results
        chestsWithWantedItem.clear();

        if (sbKey.endsWith("_ambiguous")) {
            sbKey = sbKey.substring(0, sbKey.length() - 10);
        }
        int relevantChests = 0;
        for (Map.Entry<BlockPos, List<ItemStack>> chestCache : chestCache.entrySet()) {
            boolean hasItemBeenFoundInChest = false;
            for (ItemStack item : chestCache.getValue()) {
                String key = item.hasDisplayName() ? item.getDisplayName() : item.getUnlocalizedName();
                if (item.hasTagCompound()) {
                    key = item.getTagCompound().getCompoundTag("ExtraAttributes").getString("id");
                }
                if (sbKey.equals(key)) {
                    if (!hasItemBeenFoundInChest) {
                        chestsWithWantedItem.add(chestCache.getKey());
                        hasItemBeenFoundInChest = true;
                        ++relevantChests;
                    }
                    amount -= item.stackSize;
                }
            }
            if (amount <= 0) {
                // already found all relevant chests
                break;
            }
        }
        main.getChatHelper().sendMessage(EnumChatFormatting.GREEN, "Chest Tracker & Analyzer is now highlighting " + EnumChatFormatting.LIGHT_PURPLE + relevantChests + EnumChatFormatting.GREEN + " chest" + (relevantChests > 1 ? "s" : "") + " with " + itemName
                + EnumChatFormatting.GREEN + ". Re-opening the chest analysis results with " + EnumChatFormatting.GRAY + "/moo analyzeChests " + EnumChatFormatting.GREEN + "clears the current search.");
    }

    public enum Updating {
        UNDEFINED, BAZAAR, LOWEST_BINS
    }
}

package com.sweattypalms.skyblock.ui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class PaginatedGUI extends BaseGUI {
    private final int contentSize;
    protected int currentPage = 0;

    protected PaginatedGUI(int size, String title) {
        super(size, title);
        this.contentSize = Math.max(1, size - 9);
    }

    @Override
    public final void initializeItems(Player player) {
        inventory.clear();

        List<ClickableItem> items = getPaginatedItems(player);
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / contentSize));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        int startIndex = currentPage * contentSize;
        int endIndex = Math.min(startIndex + contentSize, items.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            setItemAt(slot++, items.get(i));
        }

        fillBorder(BorderType.BOTTOM);

        if (currentPage > 0) {
            setItemAt(SIZE - 6, previousPageButton());
        }
        if (currentPage < totalPages - 1) {
            setItemAt(SIZE - 4, NEXT_PAGE(p -> {
                currentPage++;
                open(p);
            }));
        }
        setItemAt(SIZE - 5, pageIndicator(totalPages));
        setItemAt(SIZE - 1, CLOSE_GUI());
    }

    private ClickableItem previousPageButton() {
        ItemStack previousPage = new ItemStack(Material.ARROW);
        ItemMeta meta = previousPage.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Previous Page");
            previousPage.setItemMeta(meta);
        }

        return new ClickableItem(previousPage, p -> {
            if (currentPage <= 0) return;
            currentPage--;
            open(p);
        });
    }

    private ClickableItem pageIndicator(int totalPages) {
        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta meta = indicator.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Page " + (currentPage + 1) + "/" + totalPages);
            indicator.setItemMeta(meta);
        }
        return new ClickableItem(indicator, p -> {
        });
    }

    protected abstract List<ClickableItem> getPaginatedItems(Player player);
}

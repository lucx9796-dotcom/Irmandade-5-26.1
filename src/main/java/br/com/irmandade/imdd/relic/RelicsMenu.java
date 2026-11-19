package br.com.irmandade.imdd.relic;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** GUI 9x3 do /relics: clique esquerdo ou direito entrega uma cópia; o item continua na GUI. */
public class RelicsMenu extends ChestMenu {

    private static Component title() {
        MutableComponent t = Component.literal("IMDD").setStyle(Style.EMPTY.withColor(0x8844eb).withBold(true).withItalic(false));
        t.append(Component.literal("»").setStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(false).withItalic(false)));
        t.append(Component.literal(" Relíquia").setStyle(Style.EMPTY.withColor(0x2e2e2e).withBold(false).withItalic(false)));
        return t;
    }

    public RelicsMenu(int syncId, Inventory playerInv, Container container) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, container, 3);
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(27);
        List<ItemStack> relics = RelicRegistry.stacks();
        for (int i = 0; i < Math.min(27, relics.size()); i++) inv.setItem(i, relics.get(i).copy());
        player.openMenu(new SimpleMenuProvider(
                (syncId, pInv, p) -> new RelicsMenu(syncId, pInv, inv), title()));
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < 27 && clickType == ContainerInput.PICKUP && (button == 0 || button == 1)) {
            ItemStack shown = getSlot(slotIndex).getItem();
            if (!shown.isEmpty()) {
                ItemStack give = shown.copy();
                player.getInventory().add(give);
                if (!give.isEmpty()) player.drop(give, false);
            }
        }
        // Nenhuma outra interação é permitida; ressincroniza o cliente (cursor/slots)
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}

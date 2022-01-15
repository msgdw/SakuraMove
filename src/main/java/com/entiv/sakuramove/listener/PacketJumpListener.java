package com.entiv.sakuramove.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.entiv.sakuramove.Main;
import com.entiv.sakuramove.action.DoubleJump;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.lang.reflect.InvocationTargetException;

public class PacketJumpListener extends PacketAdapter implements Listener {

    private final DoubleJump doubleJump = DoubleJump.getInstance();

    public PacketJumpListener() {
        super(Main.getInstance(), PacketType.Play.Server.ABILITIES, PacketType.Play.Client.ABILITIES);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // 如果服务器更新玩家 canFly 状态 强制改为true 让玩家以为自己能飞行
        event.getPacket().getBooleans().write(3, true);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        // 仅在玩家切换飞行状态的时候会接收到 Abilities 数据包
        Player player = event.getPlayer();
        // 如果玩家不被允许飞行 则该效果是二连跳
        if (!player.getAllowFlight()) {
            // 阻止数据包
            event.setCancelled(true);

            // 二段跳
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (doubleJump.canAccept(player)) {
                    doubleJump.accept(player);
                }

                // 跳完之后关闭飞行 避免空中连跳(落地之后在开起来)
                sendAllowFlightPacket(player, false);
            });
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 玩家落地后 发送一个允许飞行的数据包
        if (player.isOnGround()) {
            sendAllowFlightPacket(player, true);
        }
    }

    /**
     * 发送允许飞行数据包
     *
     * @param player 玩家
     * @param canFly 是否可以飞行(假的)
     */
    public void sendAllowFlightPacket(Player player, boolean canFly) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.ABILITIES);
        StructureModifier<Boolean> booleanModifier = packetContainer.getBooleans();
        booleanModifier.write(0, player.isInvulnerable());
        booleanModifier.write(1, player.isFlying());
        booleanModifier.write(2, canFly);
        booleanModifier.write(3, player.getGameMode() == GameMode.CREATIVE);
        StructureModifier<Float> floatModifier = packetContainer.getFloat();
        floatModifier.write(0, player.getFlySpeed());
        floatModifier.write(1, player.getWalkSpeed());
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packetContainer, false);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}

package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Custom payload for trim sync packets from Paper servers.
 * Format matches what TrimSyncNetwork sends on the Bukkit side:
 * - int: x position
 * - int: y position
 * - int: z position
 * - boolean: has trim
 * - if has trim:
 *   - short + bytes: pattern (UTF string)
 *   - short + bytes: material (UTF string)
 */
public record TrimSyncPayload(int x, int y, int z, ShulkerTrim trim) implements CustomPayload {

    public static final Identifier CHANNEL_ID = Identifier.of("shulker_trims", "sync");
    public static final CustomPayload.Id<TrimSyncPayload> ID = new CustomPayload.Id<>(CHANNEL_ID);

    public static final PacketCodec<PacketByteBuf, TrimSyncPayload> CODEC = PacketCodec.of(
        TrimSyncPayload::write,
        TrimSyncPayload::read
    );

    private void write(PacketByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(trim != null);
        if (trim != null) {
            writeUTF(buf, trim.pattern());
            writeUTF(buf, trim.material());
        }
    }

    public static TrimSyncPayload read(PacketByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        boolean hasTrim = buf.readBoolean();
        ShulkerTrim trim = null;
        if (hasTrim) {
            String pattern = readUTF(buf);
            String material = readUTF(buf);
            trim = new ShulkerTrim(pattern, material);
        }
        return new TrimSyncPayload(x, y, z, trim);
    }

    private static void writeUTF(PacketByteBuf buf, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUTF(PacketByteBuf buf) {
        int length = buf.readShort();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

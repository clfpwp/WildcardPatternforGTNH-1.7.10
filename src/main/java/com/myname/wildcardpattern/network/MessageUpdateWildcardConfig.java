package com.myname.wildcardpattern.network;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import com.myname.wildcardpattern.ModItems;
import com.myname.wildcardpattern.item.WildcardPatternState;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MessageUpdateWildcardConfig implements IMessage {

    private int slot;
    private NBTTagCompound config;

    public MessageUpdateWildcardConfig() {}

    public MessageUpdateWildcardConfig(int slot, NBTTagCompound config) {
        this.slot = slot;
        this.config = config;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.slot = buffer.readInt();
        this.config = ByteBufUtils.readTag(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.slot);
        ByteBufUtils.writeTag(buffer, this.config);
    }

    public static class Handler implements IMessageHandler<MessageUpdateWildcardConfig, IMessage> {

        @Override
        public IMessage onMessage(MessageUpdateWildcardConfig message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (message.slot < 0 || message.slot >= player.inventory.mainInventory.length) {
                return null;
            }

            ItemStack stack = player.inventory.getStackInSlot(message.slot);
            if (stack == null || stack.getItem() != ModItems.wildcardPattern) {
                return null;
            }

            WildcardPatternGenerator.markAsWildcard(stack);
            WildcardPatternState.applyConfig(stack, message.config);
            player.inventory.markDirty();
            return null;
        }
    }
}

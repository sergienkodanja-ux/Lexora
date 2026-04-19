package com.lexoravisauls.client.mixin;

import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Session.class)
public interface SessionAccessor {
    @Mutable
    @Accessor("username")
    void setUsername(String username);
}
package com.sweattypalms.skyblock.core.mobs.ai;

import com.sweattypalms.skyblock.core.mobs.ai.v1_20_R1.VolatileAIHandlerImpl;

public final class MobAI {

    private static final VolatileAIHandler AI_HANDLER = new VolatileAIHandlerImpl();

    private MobAI() {
    }

    public static VolatileAIHandler getHandler() {
        return AI_HANDLER;
    }
}


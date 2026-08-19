package polfg.brainrot.client;

import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import polfg.brainrot.Brainrot;
import polfg.brainrot.entity.BrainrotMob;
import polfg.brainrot.registry.ModEntities;

/**
 * Клиентская часть.
 *
 * В 1.21.4 рендер энтити работает через render state: рендерер больше не читает
 * сущность напрямую во время отрисовки, а сначала снимает с неё снапшот нужных
 * полей. Отсюда три параметра-типа у MobRenderer — сущность, её состояние и модель.
 *
 * На каркасе брейнрот намеренно рисуется ванильной моделью свиньи: задача первого
 * билда не в красоте, а в том чтобы доказать, что связка "свой энтити + свой
 * рендерер" собирается. Свои модели приедут в задаче про брейнротов.
 */
@EventBusSubscriber(modid = Brainrot.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BRAINROT.get(), BrainrotRenderer::new);
    }

    /** Временный рендерер: ванильная модель и текстура свиньи. */
    public static class BrainrotRenderer extends MobRenderer<BrainrotMob, PigRenderState, PigModel> {

        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");

        public BrainrotRenderer(EntityRendererProvider.Context context) {
            super(context, new PigModel(context.bakeLayer(ModelLayers.PIG)), 0.7F);
        }

        @Override
        public PigRenderState createRenderState() {
            return new PigRenderState();
        }

        @Override
        public ResourceLocation getTextureLocation(PigRenderState state) {
            return TEXTURE;
        }
    }
}

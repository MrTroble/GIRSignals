package eu.gir.girsignals.blocks.boards;

import java.util.Map;

import eu.gir.girsignals.EnumSignals.CrossingSign;
import eu.gir.girsignals.EnumSignals.OtherSignal;
import eu.gir.girsignals.SEProperty;
import eu.gir.girsignals.SEProperty.ChangeableStage;
import eu.gir.girsignals.blocks.Signal;
import eu.gir.girsignals.init.GIRItems;
import eu.gir.girsignals.tileentitys.SignalTileEnity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public class SignalOther extends Signal {

    public SignalOther() {
        super(builder(GIRItems.SIGN_PLACEMENT_TOOL, "othersignal").height(1).signScale(3.5f)
                .offsetX(-5).offsetY(1.8f).noLink().build());
    }

    public static final SEProperty<OtherSignal> OTHERTYPE = SEProperty.of("othertype",
            OtherSignal.HM, ChangeableStage.GUISTAGE, false);
    public static final SEProperty<CrossingSign> RAILROADCROSSING = SEProperty.of(
            "railroadcrossing", CrossingSign.OFF, ChangeableStage.GUISTAGE, true,
            check(OTHERTYPE, OtherSignal.RC1).or(check(OTHERTYPE, OtherSignal.RC1))
                    .or(check(OTHERTYPE, OtherSignal.RC1)));

    @Override
    public boolean canHaveCustomname(final Map<SEProperty<?>, Object> map) {
        return true;
    }

    @Override
    public void renderSingleOverlay(final String[] display, final FontRenderer font,
            final SignalTileEnity te) {
        super.renderSingleOverlay(display, font, te);

        if (te.getProperty(OTHERTYPE).filter(OtherSignal.HM2::equals).isPresent()) {
            GlStateManager.rotate(180, 0, 1, 0);
            super.renderSingleOverlay(display, font, te);
        }
    }

    @Override
    public void renderOverlay(final double x, final double y, final double z,
            final SignalTileEnity te, final FontRenderer font) {
        super.renderOverlay(x, y, z, te, font,
                te.getProperty(OTHERTYPE)
                        .filter(type -> type.equals(OtherSignal.HM) || type.equals(OtherSignal.HM2))
                        .isPresent() ? 2.1f : this.prop.customNameRenderHeight);
    }

    @Override
    public int getHeight(final Map<SEProperty<?>, Object> map) {
        final OtherSignal other = (OtherSignal) map.get(OTHERTYPE);
        if (other == null)
            return super.getHeight(map);
        switch (other) {
            case CROSS:
                return 0;
            case ZS10:
                return 2;
            default:
                return super.getHeight(map);
        }
    }
}

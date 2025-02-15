package gg.xp.xivsupport.speech;

import gg.xp.reevent.events.Event;
import gg.xp.xivdata.data.ActionIcon;
import gg.xp.xivdata.data.ActionLibrary;
import gg.xp.xivdata.data.HasIconURL;
import gg.xp.xivdata.data.HasOptionalIconURL;
import gg.xp.xivdata.data.StatusEffectLibrary;
import gg.xp.xivsupport.events.actlines.events.HasAbility;
import gg.xp.xivsupport.events.actlines.events.HasPrimaryValue;
import gg.xp.xivsupport.events.actlines.events.HasStatusEffect;
import gg.xp.xivsupport.gui.tables.renderers.IconTextRenderer;
import gg.xp.xivsupport.gui.tables.renderers.RenderUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface CalloutEvent extends Event, HasPrimaryValue {
	@Nullable String getVisualText();

	@Nullable String getCallText();

	boolean isExpired();

	@Override
	default String getPrimaryValue() {
		return getCallText();
	}

	@Nullable CalloutEvent replaces();

	void setReplaces(CalloutEvent replaces);

	@Override
	default boolean shouldSave() {
		return false;
	}

	default @Nullable Component graphicalComponent() {
		return null;
//		return IconTextRenderer.getStretchyIcon(RenderUtils.guessIconFor(getParent()));
	}

	@Nullable Color getColorOverride();

}

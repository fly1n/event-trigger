package gg.xp.xivsupport.gui.overlay;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.Platform;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.DoubleSetting;
import gg.xp.xivsupport.persistence.settings.IntSetting;
import gg.xp.xivsupport.persistence.settings.LongSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.concurrent.atomic.AtomicLong;

// TODO: also have a method for getting a config panel, much like PluginTab
@SuppressWarnings("NumericCastThatLosesPrecision")
public class XivOverlay {
	private static final Logger log = LoggerFactory.getLogger(XivOverlay.class);
	private static final Color panelBackgroundEditMode = new Color(255, 192, 192, 64);
	private static final Color panelBackgroundDefault = new Color(0, 0, 0, 0);
	private static final Border editBorderPink = new LineBorder(Color.PINK, 5);
	private final TitledBorder editBorder;
	private static final Border transparentBorderLine = new LineBorder(new Color(0, 0, 0, 0), 5);
	private static final TitledBorder transparentBorder = new TitledBorder(transparentBorderLine, "Foo");
	public static final String bufferNumSettingKey = "xiv-overlay.buffer-strategy";

	static {
		transparentBorder.setTitleColor(new Color(0, 0, 0, 0));
	}

	private final OverlayConfig oc;
	private long minFrameTime;
	private long maxFrameTime;

	private int dragX;
	private int dragY;
	private final ScalableJFrame frame;
	private final JPanel panel;
	private final LongSetting xSetting;
	private final LongSetting ySetting;
	private final DoubleSetting opacity;
	private final DoubleSetting scaleFactor;
	private final BooleanSetting enabled;
	private final String title;

	private volatile int x;
	private volatile int y;
	private volatile boolean posSettingDirty;

	private boolean visible;
	private boolean editMode;

	private static final AtomicLong nextDefaultPos = new AtomicLong(200);


	public XivOverlay(String title, String settingKeyBase, OverlayConfig oc, PersistenceProvider persistence) {
		editBorder = new TitledBorder(editBorderPink, title);
		this.oc = oc;
		xSetting = new LongSetting(persistence, String.format("xiv-overlay.window-pos.%s.x", settingKeyBase), nextDefaultPos.get());
		ySetting = new LongSetting(persistence, String.format("xiv-overlay.window-pos.%s.y", settingKeyBase), nextDefaultPos.getAndAdd(80));
		opacity = new DoubleSetting(persistence, String.format("xiv-overlay.window-pos.%s.opacity", settingKeyBase), 1.0d, 0.0, 1.0);
		scaleFactor = new DoubleSetting(persistence, String.format("xiv-overlay.window-pos.%s.scale", settingKeyBase), 1.0d, 0.8d, 8);
		enabled = new BooleanSetting(persistence, String.format("xiv-overlay.enable.%s.enabled", settingKeyBase), false);
		int numBuffers = new IntSetting(persistence, bufferNumSettingKey, 0).get();
		enabled.addListener(this::recalc);
		frame = ScalableJFrame.construct(title, scaleFactor.get(), numBuffers);
		frame.setIgnoreRepaint(oc.getIgnoreRepaint().get());
		opacity.addListener(() -> frame.setOpacity((float) opacity.get()));
//		frame.setScaleFactor(scaleFactor.get());
		this.title = title;
		frame.setUndecorated(true);
		frame.setBackground(panelBackgroundDefault);
		frame.setType(Window.Type.UTILITY);
		panel = new JPanel();
		panel.setOpaque(false);
//		panel.setBackground(new Color(0, 0, 0, 0));
//			panel.add(contents);
		panel.setBorder(transparentBorder);
		frame.getContentPane().setLayout(new FlowLayout(FlowLayout.LEFT));
		frame.getContentPane().add(panel);
		frame.setAlwaysOnTop(true);
		resetPositionFromSettings();
		xSetting.addListener(this::resetPositionFromSettings);
		ySetting.addListener(this::resetPositionFromSettings);
		scaleFactor.addListener(this::redoScale);
		frame.setOpacity((float) opacity.get());
		frame.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent ev) {
				dragX = ev.getXOnScreen();
				dragY = ev.getYOnScreen();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				flushPosition();
			}
		});
		frame.addMouseWheelListener(ev -> {
					int scrollAmount = ev.getWheelRotation();
					double currentScale = getScale();
					if (scrollAmount > 0) {
						if (currentScale > 0.8) {
							double newScale = (Math.round((currentScale - 0.1) * 10)) / 10.0;
							setScale(newScale);
						}
					}
					else if (scrollAmount < 0) {
						if (currentScale < 8) {
							double newScale = (Math.round((currentScale + 0.1) * 10)) / 10.0;
							setScale(newScale);
						}
					}
				}
		);
		frame.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent evt) {
				int newX = evt.getXOnScreen();
				int newY = evt.getYOnScreen();
				int deltaX = newX - dragX;
				int deltaY = newY - dragY;
				Point old = frame.getLocation();
				setPosition(old.x + deltaX, old.y + deltaY);
				dragX = newX;
				dragY = newY;
			}
		});
		calcFrameTimes();
		oc.getMaxFps().addListener(this::calcFrameTimes);
		oc.getMinFps().addListener(this::calcFrameTimes);
	}

	public void resetPositionFromSettings() {
		if (posSettingDirty) {
			return;
		}
		frame.setLocation((int) xSetting.get(), (int) ySetting.get());
	}

	public void finishInit() {
		redoScale();
	}

	protected void redoScale() {
		frame.setScaleFactor(scaleFactor.get());
	}

	public String getTitle() {
		return title;
	}

	protected JFrame getFrame() {
		return frame;
	}

	protected JPanel getPanel() {
		return panel;
	}

	public BooleanSetting getEnabled() {
		return enabled;
	}

	public void setPosition(int x, int y) {
		posSettingDirty = true;
		this.x = x;
		this.y = y;
		frame.setLocation(x, y);
	}

	private void flushPosition() {
		if (!posSettingDirty) {
			return;
		}
		xSetting.set(x);
		ySetting.set(y);
		posSettingDirty = false;
	}

	public LongSetting getXSetting() {
		return xSetting;
	}

	public LongSetting getYSetting() {
		return ySetting;
	}

	public void setVisible(boolean visible) {
		// TODO: a bit of a hack, but fixes the bug where if you start the program with overlays disabled, you will
		// have to restart after enabling them.
		if (visible && frame.getWidth() < 30) {
			repackSize();
		}
		this.visible = visible;
		recalc();
	}

	protected boolean isVisible() {
		return visible;
	}

	protected void repackSize() {
		getFrame().revalidate();
		redoScale();
	}

	private void recalc() {
		if (!editMode) {
			panel.setBorder(transparentBorder);
		}
		boolean visible = this.visible && enabled.get();
		if (visible && !frame.isVisible()) {
			frame.setVisible(true);
		}
		panel.setVisible(visible);
		setClickThrough(frame, !editMode);
		frame.setFocusable(editMode);
		if (editMode) {
			panel.setBorder(editBorder);
		}
	}

	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
		recalc();
	}

	public DoubleSetting opacity() {
		return this.opacity;
	}

	public void setScale(double scale) {
		boolean wasVisible = frame.isVisible();
		frame.setVisible(false);
		this.scaleFactor.set(scale);
		frame.setVisible(wasVisible);
	}

	public double getScale() {
		return scaleFactor.get();
	}

	public DoubleSetting getScaleSetting() {
		return scaleFactor;
	}

	// https://docs.microsoft.com/en-us/windows/win32/winmsg/extended-window-styles
	private static final long WS_NOACTIVATE = 0x08000000L;

	private static void setClickThrough(JFrame w, boolean clickThrough) {
		log.trace("Click-through: {}", clickThrough);
		w.setFocusableWindowState(!clickThrough);
		if (!Platform.isWindows()) {
			log.warn("Setting click-through is not supported on non-Windows platforms at this time.");
			return;
		}
		WinDef.HWND hwnd = getHWnd(w);
		int wl = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
		if (clickThrough) {
			wl |= WinUser.WS_EX_TRANSPARENT;
			wl |= WS_NOACTIVATE;
		}
		else {
			wl &= ~WinUser.WS_EX_TRANSPARENT;
			wl &= ~WS_NOACTIVATE;
		}
		w.setBackground(new Color(0, 0, 0, 0));
		User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, wl);
	}

	/**
	 * Get the window handle from the OS
	 */
	private static WinDef.HWND getHWnd(Component w) {
		WinDef.HWND hwnd = new WinDef.HWND();
		hwnd.setPointer(Native.getComponentPointer(w));
		return hwnd;
	}

	private void calcFrameTimes() {
		minFrameTime = 1000 / oc.getMaxFps().get();
		maxFrameTime = 1000 / oc.getMinFps().get();
	}

	protected long calculateScaledFrameTime(long basis) {
		return Math.min(Math.max((long) (basis / getScale()), maxFrameTime), minFrameTime);
	}

	protected long calculateUnscaledFrameTime(long basis) {
		return Math.min(Math.max(basis, maxFrameTime), minFrameTime);
	}

}

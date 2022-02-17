package eu.gir.girsignals.signalbox;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.lwjgl.util.Point;

import com.google.common.collect.Maps;

import eu.gir.girsignals.signalbox.SignalBoxUtil.EnumGUIMode;
import eu.gir.guilib.ecs.interfaces.UIAutoSync;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Rotation;

public class SignalNode implements UIAutoSync {
	
	private final String MODE = "mode";
	private final String ROTATION = "rotation";
	private final String OPTION = "option";
	
	private final Point point;
	private HashMap<Entry<Point, Point>, Entry<EnumGUIMode, Rotation>> possibleConnections = new HashMap<>();
	private HashMap<Entry<EnumGUIMode, Rotation>, PathOption> possibleModes = new HashMap<>();
	
	public SignalNode(Point point) {
		this.point = point;
	}
	
	public void add(EnumGUIMode mode, Rotation rot) {
		possibleModes.put(Maps.immutableEntry(mode, rot), new PathOption());
	}
	
	public boolean has(EnumGUIMode mode, Rotation rot) {
		return possibleModes.containsKey(Maps.immutableEntry(mode, rot));
	}
	
	public void remove(EnumGUIMode mode, Rotation rot) {
		possibleModes.remove(Maps.immutableEntry(mode, rot));
	}
		
	public void post() {
		possibleModes.forEach((e, i) -> {
			final Point p1 = new Point(this.point);
			final Point p2 = new Point(this.point);
			switch (e.getKey()) {
			case CORNER:
				switch (e.getValue()) {
				case NONE:
					p1.translate(0, 1);
					p2.translate(-1, 0);
					break;
				case CLOCKWISE_90:
					p1.translate(0, -1);
					p2.translate(-1, 0);
					break;
				case CLOCKWISE_180:
					p1.translate(0, -1);
					p2.translate(1, 0);
					break;
				case COUNTERCLOCKWISE_90:
					p1.translate(0, 1);
					p2.translate(1, 0);
					break;
				default:
					return;
				}
				break;
			case STRAIGHT:
				switch (e.getValue()) {
				case NONE:
				case CLOCKWISE_180:
					p1.translate(1, 0);
					p2.translate(-1, 0);
					break;
				case CLOCKWISE_90:
				case COUNTERCLOCKWISE_90:
					p1.translate(0, 1);
					p2.translate(0, -1);
					break;
				default:
					return;
				}
				break;
			default:
				return;
			}
			possibleConnections.put(Maps.immutableEntry(p1, p2), e);
		});
	}
	
	public Point getPoint() {
		return point;
	}
	
	public Set<Entry<Point, Point>> connections() {
		return this.possibleConnections.keySet();
	}
	
	public void applyNormal(Entry<EnumGUIMode, Rotation> guimode, Consumer<PathOption> applier) {
		if (guimode != null && this.possibleModes.containsKey(guimode))
			applier.accept(this.possibleModes.get(guimode));
	}
	
	public void apply(Entry<Point, Point> entry, Consumer<PathOption> applier) {
		Entry<EnumGUIMode, Rotation> guimode = null;
		if (this.possibleConnections.containsKey(entry)) {
			guimode = this.possibleConnections.get(entry);
		} else {
			Entry<Point, Point> points = Maps.immutableEntry(entry.getValue(), entry.getKey());
			if (this.possibleConnections.containsKey(points)) {
				guimode = this.possibleConnections.get(points);
			}
		}
		applyNormal(guimode, applier);
	}
	
	public void forEach(BiConsumer<Entry<EnumGUIMode, Rotation>, PathOption> applier) {
		possibleModes.forEach(applier);
	}
	
	public boolean isEmpty() {
		return possibleModes.isEmpty();
	}
	
	@Override
	public void write(NBTTagCompound compound) {
		if(possibleModes.isEmpty())
			return;
		final NBTTagList pointList = new NBTTagList();
		possibleModes.forEach((mode, option) -> {
			final NBTTagCompound entry = new NBTTagCompound();
			entry.setString(MODE, mode.getKey().name());
			entry.setString(ROTATION, mode.getValue().name());
			entry.setTag(OPTION, option.writeNBT());
			pointList.appendTag(entry);
		});
		compound.setTag(this.getID(), pointList);
	}
	
	@Override
	public void read(NBTTagCompound compound) {
		if(!compound.hasKey(getID()))
			return;
		final NBTTagList pointList = (NBTTagList) compound.getTag(getID());
		pointList.forEach(e -> {
			final NBTTagCompound entry = (NBTTagCompound) e;
			final EnumGUIMode mode = EnumGUIMode.valueOf(entry.getString(MODE));
			final Rotation rotation = Rotation.valueOf(entry.getString(ROTATION));
			final Entry<EnumGUIMode, Rotation> modeRotation = Maps.immutableEntry(mode, rotation);
			possibleModes.put(modeRotation, new PathOption(entry.getCompoundTag(OPTION)));
		});
	}
	
	@Override
	public String getID() {
		return point.getX() + "." + point.getY();
	}
	
	@Override
	public void setID(String id) {
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof SignalNode) {
			this.point.equals(((SignalNode) obj).getPoint());
		}
		return super.equals(obj);
	}

	public Optional<PathOption> getOption(final EnumGUIMode mode) {
		final Optional<Entry<Entry<EnumGUIMode, Rotation>, PathOption>> opt = this.possibleModes.entrySet().stream().filter(e -> e.getKey().getKey().equals(mode)).findFirst();
		if(opt.isPresent()) {
			return Optional.of(opt.get().getValue());
		} else {
			return Optional.empty();
		}
	}
	
	public boolean has(EnumGUIMode mode) {
		return this.possibleModes.keySet().stream().anyMatch(e -> e.getKey().equals(mode));
	}
}

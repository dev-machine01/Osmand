package net.osmand.plus.osmedit;

import net.osmand.NativeLibrary;
import net.osmand.PlatformUtil;
import net.osmand.data.Amenity;
import net.osmand.data.Building;
import net.osmand.data.LatLon;
import net.osmand.data.MapObject;
import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.PoiType;
import net.osmand.osm.edit.Entity;
import net.osmand.osm.edit.EntityInfo;
import net.osmand.osm.edit.Node;
import net.osmand.osm.edit.OSMSettings.OSMTagKey;
import net.osmand.osm.edit.Way;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenstreetmapLocalUtil implements OpenstreetmapUtil {

	public final static Log LOG = PlatformUtil.getLog(OpenstreetmapLocalUtil.class);

	private OsmEditingPlugin plugin;

	public OpenstreetmapLocalUtil(OsmEditingPlugin plugin) {
		this.plugin = plugin;
	}

	private List<OnNodeCommittedListener> listeners = new ArrayList<>();

	public void addNodeCommittedListener(OnNodeCommittedListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeNodeCommittedListener(OnNodeCommittedListener listener) {
		listeners.remove(listener);
	}

	@Override
	public EntityInfo getEntityInfo(long id) {
		return null;
	}
	
	@Override
	public Entity commitEntityImpl(OsmPoint.Action action, Entity entity, EntityInfo info, String comment,
	                               boolean closeChangeSet, Set<String> changedTags) {
		Entity newEntity = entity;
		if (entity.getId() == -1) {
			if (entity instanceof Node) {
				newEntity = new Node((Node) entity, Math.min(-2, plugin.getDBPOI().getMinID() - 1));
			} else if (entity instanceof Way) {
				newEntity = new Way(Math.min(-2, plugin.getDBPOI().getMinID() - 1), ((Way) entity).getNodeIds(), entity.getLatitude(), entity.getLongitude());
			} else {
				return null;
			}
		}
		OpenstreetmapPoint p = new OpenstreetmapPoint();
		newEntity.setChangedTags(changedTags);
		p.setEntity(newEntity);
		p.setAction(action);
		p.setComment(comment);
		if (p.getAction() == OsmPoint.Action.DELETE && newEntity.getId() < 0) { //if it is our local poi
			plugin.getDBPOI().deletePOI(p);
		} else {
			plugin.getDBPOI().addOpenstreetmap(p);
		}
		for (OnNodeCommittedListener listener : listeners) {
			listener.onNoteCommitted();
		}
		return newEntity;
	}
	
	@Override
	public Entity loadEntity(MapObject mapObject) {
		boolean amenity = mapObject instanceof Amenity;
		PoiType poiType = null;
		if (amenity) {
			poiType = ((Amenity) mapObject).getType().getPoiTypeByKeyName(((Amenity) mapObject).getSubType());
		}
		boolean isWay = mapObject.getId() % 2 == 1; // check if amenity is a way
		if (poiType == null && amenity) {
			return null;
		}
		long entityId;
		if (mapObject instanceof Amenity) {
			entityId = mapObject.getId() >> 1;
		} else {
			entityId = mapObject.getId() >> 7;
		}

		Entity entity;
		LatLon loc = mapObject.getLocation();
		if (loc == null) {
			if (mapObject instanceof NativeLibrary.RenderedObject) {
				loc = ((NativeLibrary.RenderedObject) mapObject).getLabelLatLon();
			} else if (mapObject instanceof Building) {
				loc = ((Building) mapObject).getLatLon2();
			}
		}
		if (loc == null) {
			return null;
		}
		if (isWay) {
			entity = new Way(entityId, null, loc.getLatitude(), loc.getLongitude());
		} else {
			entity = new Node(loc.getLatitude(), loc.getLongitude(), entityId);
		}
		if (poiType != null) {
			entity.putTagNoLC(EditPoiData.POI_TYPE_TAG, poiType.getTranslation());
			if (poiType.getOsmTag2() != null) {
				entity.putTagNoLC(poiType.getOsmTag2(), poiType.getOsmValue2());
			}
		}
		if (!Algorithms.isEmpty(mapObject.getName())) {
			entity.putTagNoLC(OSMTagKey.NAME.getValue(), mapObject.getName());
		}
		if (amenity) {
			if (!Algorithms.isEmpty(((Amenity) mapObject).getOpeningHours())) {
				entity.putTagNoLC(OSMTagKey.OPENING_HOURS.getValue(), ((Amenity) mapObject).getOpeningHours());
			}
			for (Map.Entry<String, String> entry : ((Amenity) mapObject).getAdditionalInfo().entrySet()) {
				AbstractPoiType abstractPoi = MapPoiTypes.getDefault().getAnyPoiAdditionalTypeByKey(entry.getKey());
				if (abstractPoi != null && abstractPoi instanceof PoiType) {
					PoiType p = (PoiType) abstractPoi;
					if (!p.isNotEditableOsm() && !Algorithms.isEmpty(p.getEditOsmTag())) {
						entity.putTagNoLC(p.getEditOsmTag(), entry.getValue());
					}
				}
			}
		}

		// check whether this is node (because id of node could be the same as relation)
		if (entity instanceof Node && MapUtils.getDistance(entity.getLatLon(), mapObject.getLocation()) < 50) {
			return entity;
		} else if (entity instanceof Way) {
			return entity;
		}
		return null;
	}

	@Override
	public void closeChangeSet() {
	}

	public interface OnNodeCommittedListener {
		void onNoteCommitted();
	}
	
}

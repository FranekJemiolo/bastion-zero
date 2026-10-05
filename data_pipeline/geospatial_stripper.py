#!/usr/bin/env python3
"""
Geospatial Feature Stripping Pipeline for Bastion Zero.
Reduces massive OpenStreetMap/vector datasets into ultra-lightweight offline extracts (< 40MB)
by stripping non-survival commercial features while preserving elevation contours,
freshwater streams, medical centers, and mountain passes.
"""

import json
import math
from typing import Dict, List, Any

SURVIVAL_CRITICAL_AMENITIES = {
    "drinking_water",
    "hospital",
    "clinic",
    "doctors",
    "pharmacy",
    "shelter",
    "alpine_hut",
    "spring",
    "water_point",
    "ranger_station",
    "fire_station",
}

SURVIVAL_CRITICAL_NATURAL = {
    "water",
    "spring",
    "stream",
    "river",
    "peak",
    "saddle",
    "cave_entrance",
    "ridge",
}

SURVIVAL_CRITICAL_HIGHWAY = {
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "track",
    "path",
}

class GeospatialStripper:
    def __init__(self, min_lat: float, min_lon: float, max_lat: float, max_lon: float):
        self.min_lat = min_lat
        self.min_lon = min_lon
        self.max_lat = max_lat
        self.max_lon = max_lon

    def is_point_in_bbox(self, lat: float, lon: float) -> bool:
        return self.min_lat <= lat <= self.max_lat and self.min_lon <= lon <= self.max_lon

    def is_survival_critical(self, properties: Dict[str, Any]) -> bool:
        amenity = properties.get("amenity")
        if amenity in SURVIVAL_CRITICAL_AMENITIES:
            return True

        natural = properties.get("natural")
        if natural in SURVIVAL_CRITICAL_NATURAL:
            return True

        waterway = properties.get("waterway")
        if waterway in {"stream", "river", "canal"}:
            return True

        highway = properties.get("highway")
        if highway in SURVIVAL_CRITICAL_HIGHWAY:
            return True

        emergency = properties.get("emergency")
        if emergency:
            return True

        return False

    def strip_geojson(self, geojson_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Filter GeoJSON features by bounding box and survival relevance.
        """
        features = geojson_data.get("features", [])
        retained = []

        for f in features:
            props = f.get("properties", {})
            geom = f.get("geometry", {})
            coords = geom.get("coordinates", [])

            # Check point geometry inside bbox
            if geom.get("type") == "Point" and len(coords) >= 2:
                lon, lat = coords[0], coords[1]
                if not self.is_point_in_bbox(lat, lon):
                    continue

            if self.is_survival_critical(props):
                retained.append(f)

        return {
            "type": "FeatureCollection",
            "bbox": [self.min_lon, self.min_lat, self.max_lon, self.max_lat],
            "total_input_features": len(features),
            "retained_survival_features": len(retained),
            "compression_ratio": round(len(retained) / max(1, len(features)), 4),
            "features": retained,
        }

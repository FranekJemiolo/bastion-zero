import unittest
from geospatial_stripper import GeospatialStripper

class TestGeospatialStripper(unittest.TestCase):

    def setUp(self):
        # Bounding box around Bieszczady mountains, Poland
        self.stripper = GeospatialStripper(
            min_lat=49.0,
            min_lon=22.0,
            max_lat=49.5,
            max_lon=22.8,
        )

    def test_retains_critical_survival_amenities(self):
        sample_geojson = {
            "type": "FeatureCollection",
            "features": [
                {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [22.4, 49.2]},
                    "properties": {"amenity": "drinking_water", "name": "Fresh Mountain Spring"},
                },
                {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [22.3, 49.1]},
                    "properties": {"amenity": "hospital", "name": "Regional Trauma Clinic"},
                },
                {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [22.5, 49.3]},
                    "properties": {"amenity": "coffee_shop", "name": "Urban Cafe"},
                },
                {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [22.5, 49.3]},
                    "properties": {"shop": "boutique", "name": "Fashion Store"},
                },
                {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [10.0, 10.0]}, # Out of bounds
                    "properties": {"amenity": "hospital", "name": "Faraway Hospital"},
                },
            ]
        }

        result = self.stripper.strip_geojson(sample_geojson)
        self.assertEqual(result["total_input_features"], 5)
        self.assertEqual(result["retained_survival_features"], 2)
        retained_names = [f["properties"]["name"] for f in result["features"]]
        self.assertIn("Fresh Mountain Spring", retained_names)
        self.assertIn("Regional Trauma Clinic", retained_names)
        self.assertNotIn("Urban Cafe", retained_names)
        self.assertNotIn("Fashion Store", retained_names)
        self.assertNotIn("Faraway Hospital", retained_names)

if __name__ == "__main__":
    unittest.main()

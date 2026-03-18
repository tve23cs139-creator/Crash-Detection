import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  GoogleMap,
  MarkerF,
  OverlayViewF,
  PolylineF,
  useLoadScript,
} from '@react-google-maps/api';
import { push, ref } from 'firebase/database';
import { database } from './firebase';
import './MapComponent.css';

const MAP_LIBRARIES = ['places', 'geometry','directions'];

const MIDNIGHT_STYLE = [
  { elementType: 'geometry', stylers: [{ color: '#0a0f1a' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#0a0f1a' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#7f94b2' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ color: '#17233a' }] },
  { featureType: 'poi', elementType: 'labels.text.fill', stylers: [{ color: '#8fb1e0' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ color: '#112338' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#1a2a3f' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#0f1725' }] },
  { featureType: 'road', elementType: 'labels.text.fill', stylers: [{ color: '#9db5d6' }] },
  { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#182741' }] },
  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#071120' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#4f708f' }] },
];

const mapContainerStyle = {
  width: '100%',
  height: '100%',
};

const HOSPITAL_ICON = {
  url: `data:image/svg+xml;utf8,${encodeURIComponent(`
    <svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 52 52">
      <circle cx="26" cy="26" r="24" fill="#0b1f40" stroke="#60a5fa" stroke-width="2"/>
      <path d="M22 14h8v8h8v8h-8v8h-8v-8h-8v-8h8z" fill="#60a5fa"/>
    </svg>
  `)}`,
};
// eslint-disable-next-line react/prop-types
export default function MapComponent({ crash, googleMapsApiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY }) {
  const [map, setMap] = useState(null);
  const [hospitals, setHospitals] = useState([]);
  const [routesByHospital, setRoutesByHospital] = useState({});
  const [selectedHospitalId, setSelectedHospitalId] = useState(null);
  const [dispatchedHospitalId, setDispatchedHospitalId] = useState(null);
  const [notification, setNotification] = useState('');
  const lastCrashKeyRef = useRef(null);

  const { isLoaded, loadError } = useLoadScript({
    googleMapsApiKey,
    libraries: MAP_LIBRARIES,
  });

  const crashPosition = useMemo(() => {
    if (!crash || crash.lat == null || crash.lng == null) {
      return null;
    }

    const lat = Number(crash.lat);
    const lng = Number(crash.lng);

    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return null;
    }

    return { lat, lng };
  }, [crash]);

  const selectedRoute = useMemo(() => {
    if (!selectedHospitalId) {
      return null;
    }
    return routesByHospital[selectedHospitalId]?.result ?? null;
  }, [routesByHospital, selectedHospitalId]);

  const hospitalIcon = useMemo(() => {
    if (!isLoaded || !window.google) {
      return HOSPITAL_ICON;
    }

    return {
      ...HOSPITAL_ICON,
      scaledSize: new window.google.maps.Size(34, 34),
    };
  }, [isLoaded]);

  const fitToEmergencyBounds = useCallback((nextHospitals) => {
    if (!map || !crashPosition || !window.google) {
      return;
    }

    const bounds = new window.google.maps.LatLngBounds();
    bounds.extend(crashPosition);

    nextHospitals.forEach((hospital) => {
      bounds.extend(hospital.position);
    });

    map.fitBounds(bounds, 90);
  }, [map, crashPosition]);

  const calculateRoutes = useCallback((hospitalList, crashPoint) => {
    if (!window.google || !crashPoint || hospitalList.length === 0) {
      setRoutesByHospital({});
      return;
    }

    const directionsService = new window.google.maps.DirectionsService();

    Promise.all(
      hospitalList.map(
        (hospital) =>
          new Promise((resolve) => {
            directionsService.route(
              {
                origin: hospital.position,
                destination: crashPoint,
                travelMode: window.google.maps.TravelMode.DRIVING,
                drivingOptions: {
                  departureTime: new Date(),
                  trafficModel: window.google.maps.TrafficModel.BEST_GUESS,
                },
              },
              (result, status) => {
                if (status === 'OK' && result?.routes?.[0]?.legs?.[0]) {
                  const leg = result.routes[0].legs[0];
                  resolve({
                    id: hospital.placeId,
                    result,
                    etaText: leg.duration_in_traffic?.text || leg.duration?.text || 'N/A',
                    distanceText: leg.distance?.text || 'N/A',
                  });
                  return;
                }

                resolve({
                  id: hospital.placeId,
                  result: null,
                  etaText: 'N/A',
                  distanceText: 'N/A',
                });
              },
            );
          }),
      ),
    ).then((routeResults) => {
      const nextRoutes = routeResults.reduce((acc, item) => {
        acc[item.id] = item;
        return acc;
      }, {});

      setRoutesByHospital(nextRoutes);
    });
  }, []);

  useEffect(() => {
    if (!map || !isLoaded || !crashPosition || !window.google) {
      return;
    }

    const crashKey = `${crash?.id ?? ''}-${crashPosition.lat}-${crashPosition.lng}-${crash?.timestamp ?? ''}`;
    if (lastCrashKeyRef.current === crashKey) {
      return;
    }
    lastCrashKeyRef.current = crashKey;

    setNotification('');
    setDispatchedHospitalId(null);

    const placesService = new window.google.maps.places.PlacesService(map);
    const crashLatLng = new window.google.maps.LatLng(crashPosition.lat, crashPosition.lng);

    placesService.nearbySearch(
      {
        location: crashLatLng,
        radius: 10000,
        type: 'hospital',
        keyword: 'hospital health emergency',
      },
      (results, status) => {
        if (status !== window.google.maps.places.PlacesServiceStatus.OK || !results) {
          setHospitals([]);
          setRoutesByHospital({});
          return;
        }

        const nearestThree = results
          .filter((place) => place.geometry?.location && place.place_id)
          .map((place) => {
            const lat = place.geometry.location.lat();
            const lng = place.geometry.location.lng();
            const distanceMeters = window.google.maps.geometry.spherical.computeDistanceBetween(
              crashLatLng,
              new window.google.maps.LatLng(lat, lng),
            );

            return {
              placeId: place.place_id,
              name: place.name || 'Unnamed Hospital',
              position: { lat, lng },
              distanceMeters,
              vicinity: place.vicinity || '',
            };
          })
          .sort((a, b) => a.distanceMeters - b.distanceMeters)
          .slice(0, 3);

        setHospitals(nearestThree);
        setSelectedHospitalId(nearestThree[0]?.placeId ?? null);
        fitToEmergencyBounds(nearestThree);
        calculateRoutes(nearestThree, crashPosition);
      },
    );
  }, [calculateRoutes, crash, crashPosition, fitToEmergencyBounds, isLoaded, map]);

  const handleDispatch = useCallback(async (hospital) => {
    const routeData = routesByHospital[hospital.placeId];
    const etaText = routeData?.etaText ?? 'N/A';
    const distanceText = routeData?.distanceText ?? 'N/A';

    setSelectedHospitalId(hospital.placeId);
    setDispatchedHospitalId(hospital.placeId);
    setNotification(`Ambulance dispatched from ${hospital.name}. ETA: ${etaText}.`);

    try {
      await push(ref(database, 'dispatches'), {
        crashId: crash?.id ?? null,
        crashSeverity: crash?.severity ?? null,
        crashTimestamp: crash?.timestamp ?? null,
        crashLocation: crashPosition,
        hospital: {
          placeId: hospital.placeId,
          name: hospital.name,
          location: hospital.position,
        },
        etaText,
        distanceText,
        dispatchedAt: new Date().toISOString(),
      });
    } catch (err) {
      setNotification(`Dispatch logging failed: ${err.message}`);
    }
  }, [crash, crashPosition, routesByHospital]);

  if (!googleMapsApiKey) {
    return <div className="map-error">Missing Google Maps API key. Set `VITE_GOOGLE_MAPS_API_KEY`.</div>;
  }

  if (loadError) {
    return <div className="map-error">Google Maps failed to load.</div>;
  }

  if (!isLoaded) {
    return <div className="map-loading">Loading emergency map...</div>;
  }

  if (!crashPosition) {
    return <div className="map-loading">No crash location available.</div>;
  }

  return (
    <div className="rescue-layout">
      <div className="map-shell">
        <GoogleMap
          mapContainerStyle={mapContainerStyle}
          center={crashPosition}
          zoom={13}
          onLoad={(loadedMap) => setMap(loadedMap)}
          options={{
            styles: MIDNIGHT_STYLE,
            streetViewControl: false,
            mapTypeControl: false,
            fullscreenControl: false,
            clickableIcons: false,
          }}
        >
          <OverlayViewF position={crashPosition} mapPaneName={OverlayViewF.OVERLAY_MOUSE_TARGET}>
            <div className="crash-pulse-marker" title={`Crash severity: ${crash?.severity ?? 'Unknown'}`}>
              <span className="crash-pulse-core" />
            </div>
          </OverlayViewF>

          {hospitals.map((hospital) => {
            const routeData = routesByHospital[hospital.placeId];
            return (
              <Fragment key={hospital.placeId}>
                <MarkerF
                  position={hospital.position}
                  icon={hospitalIcon}
                  onClick={() => setSelectedHospitalId(hospital.placeId)}
                />
                {routeData && (
                  <OverlayViewF
                    position={hospital.position}
                    mapPaneName={OverlayViewF.FLOAT_PANE}
                    getPixelPositionOffset={(width, height) => ({ x: -width / 2, y: -height - 18 })}
                  >
                    <div className="eta-tooltip">
                      <span>{routeData.etaText}</span>
                      <span>{routeData.distanceText}</span>
                    </div>
                  </OverlayViewF>
                )}
              </Fragment>
            );
          })}

          {selectedRoute?.routes?.[0]?.overview_path && (
            <PolylineF
              path={selectedRoute.routes[0].overview_path}
              options={{
                strokeColor: dispatchedHospitalId === selectedHospitalId ? '#22c55e' : '#38bdf8',
                strokeOpacity: 1,
                strokeWeight: dispatchedHospitalId === selectedHospitalId ? 8 : 5,
              }}
            />
          )}
        </GoogleMap>
      </div>

      <aside className="rescue-panel">
        <h3>Rescue Panel</h3>
        <p className="rescue-subtitle">Nearest Hospitals (10km)</p>

        {notification && <div className="dispatch-notification">{notification}</div>}

        {hospitals.length === 0 && <div className="rescue-empty">No nearby hospitals found.</div>}

        {hospitals.map((hospital, idx) => {
          const routeData = routesByHospital[hospital.placeId];
          const isSelected = selectedHospitalId === hospital.placeId;
          const isDispatched = dispatchedHospitalId === hospital.placeId;

          return (
            <div
              key={hospital.placeId}
              className={`hospital-card ${isSelected ? 'selected' : ''}`}
              onClick={() => setSelectedHospitalId(hospital.placeId)}
            >
              <div className="hospital-head">
                <span className="hospital-rank">#{idx + 1}</span>
                <strong>{hospital.name}</strong>
              </div>
              <div className="hospital-meta">
                <span>{(hospital.distanceMeters / 1000).toFixed(2)} km away</span>
                <span>ETA: {routeData?.etaText ?? 'Calculating...'}</span>
                <span>Distance: {routeData?.distanceText ?? 'Calculating...'}</span>
              </div>
              <button
                type="button"
                className={`dispatch-btn ${isDispatched ? 'dispatched' : ''}`}
                onClick={(event) => {
                  event.stopPropagation();
                  handleDispatch(hospital);
                }}
              >
                {isDispatched ? 'Ambulance Dispatched' : 'Dispatch Ambulance'}
              </button>
            </div>
          );
        })}
      </aside>
    </div>
  );
}

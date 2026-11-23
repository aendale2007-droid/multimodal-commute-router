import React, { useState } from 'react';
import {
  StyleSheet, Text, View, TouchableOpacity, ScrollView,
  ActivityIndicator, TextInput, Keyboard
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Slider from '@react-native-community/slider';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8082';
const API_URL = `${BASE_URL}/api/plan`;

interface PlaceResult {
  name: string;
  subtitle: string;
  lat: number;
  lon: number;
}

interface RouteLeg {
  mode: string;
  fromName: string;
  toName: string;
  durationMinutes: number;
  routeShortName: string;
}

interface RankedItinerary {
  summary: string;
  durationMinutes: number;
  estimatedCostUsd: number;
  transfers: number;
  finalScore: number;
  legs: RouteLeg[];
}

export default function HomeScreen() {
  const [originQuery, setOriginQuery] = useState('Federal Way Transit Center');
  const [destQuery, setDestQuery] = useState('Westlake Center');
  const [origin, setOrigin] = useState<PlaceResult>({
    name: 'Federal Way Transit Center',
    subtitle: 'Federal Way, WA',
    lat: 47.3168,
    lon: -122.3046,
  });
  const [dest, setDest] = useState<PlaceResult>({
    name: 'Westlake Center',
    subtitle: 'Seattle, WA',
    lat: 47.6114,
    lon: -122.3375,
  });

  const [activeField, setActiveField] = useState<'origin' | 'dest' | null>(null);
  const [suggestions, setSuggestions] = useState<PlaceResult[]>([]);

  const [timeWeight, setTimeWeight] = useState(0.5);
  const [routes, setRoutes] = useState<RankedItinerary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const costWeight = Math.round((1 - timeWeight) * 100) / 100;

  const geocodeText = async (query: string): Promise<PlaceResult[]> => {
    const res = await fetch(`${BASE_URL}/api/geocode?q=${encodeURIComponent(query)}`);
    if (!res.ok) return [];
    return await res.json();
  };

  const searchTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const searchPlaces = (text: string, field: 'origin' | 'dest') => {
    if (field === 'origin') setOriginQuery(text);
    else setDestQuery(text);
    setActiveField(field);

    if (searchTimer.current) clearTimeout(searchTimer.current);

    if (text.trim().length < 3) {
      setSuggestions([]);
      return;
    }

    // Wait 350ms after the user stops typing before calling Java
    searchTimer.current = setTimeout(async () => {
      try {
        const results = await geocodeText(text);
        setSuggestions(results);
      } catch {
        setSuggestions([]);
      }
    }, 350);
  };


  const selectPlace = (place: PlaceResult) => {
    if (activeField === 'origin') {
      setOrigin(place);
      setOriginQuery(place.name);
    } else if (activeField === 'dest') {
      setDest(place);
      setDestQuery(place.name);
    }
    setSuggestions([]);
    setActiveField(null);
    Keyboard.dismiss();
  };

  const findRoutes = async () => {
    setSuggestions([]);
    setActiveField(null);
    Keyboard.dismiss();
    setLoading(true);
    setError('');

    try {
      let activeOrigin = origin;
      let activeDest = dest;

      if (originQuery.trim() && originQuery.trim() !== origin.name) {
        const matches = await geocodeText(originQuery);
        if (matches.length > 0) {
          activeOrigin = matches[0];
          setOrigin(activeOrigin);
          setOriginQuery(activeOrigin.name);
        }
      }

      if (destQuery.trim() && destQuery.trim() !== dest.name) {
        const matches = await geocodeText(destQuery);
        if (matches.length > 0) {
          activeDest = matches[0];
          setDest(activeDest);
          setDestQuery(activeDest.name);
        }
      }



      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLat: activeOrigin.lat,
          originLon: activeOrigin.lon,
          destLat: activeDest.lat,
          destLon: activeDest.lon,
          timeWeight,
          costWeight,
        }),
      });
      if (!response.ok) throw new Error(`Server returned ${response.status}`);
      const data = await response.json();
      setRoutes(data);
    } catch (err: any) {
      setError(`Connection error: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Puget Sound Multimodal</Text>

      <View style={styles.card}>
        <Text style={styles.inputLabel}>From</Text>
        <TextInput
          style={styles.input}
          value={originQuery}
          onChangeText={(t) => searchPlaces(t, 'origin')}
          onFocus={() => setActiveField('origin')}
          placeholder="Search starting point..."
        />

        <Text style={styles.inputLabel}>To</Text>
        <TextInput
          style={styles.input}
          value={destQuery}
          onChangeText={(t) => searchPlaces(t, 'dest')}
          onFocus={() => setActiveField('dest')}
          placeholder="Search destination..."
        />

        {suggestions.length > 0 && activeField && (
          <View style={styles.dropdown}>
            {suggestions.map((item, idx) => (
              <TouchableOpacity
                key={idx}
                style={styles.dropdownItem}
                onPress={() => selectPlace(item)}
              >
                <Text style={styles.dropdownTitle}>{item.name}</Text>
                {item.subtitle ? <Text style={styles.dropdownSub}>{item.subtitle}</Text> : null}
              </TouchableOpacity>
            ))}
          </View>
        )}

        <Text style={styles.label}>
          Priority: {Math.round(timeWeight * 100)}% Speed / {Math.round(costWeight * 100)}% Savings
        </Text>
        <Slider
          style={{ width: '100%', height: 36 }}
          minimumValue={0}
          maximumValue={1}
          step={0.1}
          value={timeWeight}
          onValueChange={setTimeWeight}
          minimumTrackTintColor="#007AFF"
          maximumTrackTintColor="#34C759"
        />
        <View style={styles.sliderLabels}>
          <Text style={styles.smallText}>Save Money</Text>
          <Text style={styles.smallText}>Save Time</Text>
        </View>

        <TouchableOpacity style={styles.button} onPress={findRoutes}>
          <Text style={styles.buttonText}>Find Best Routes</Text>
        </TouchableOpacity>
      </View>

      {loading && <ActivityIndicator size="large" color="#007AFF" style={{ marginTop: 16 }} />}
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <ScrollView style={styles.results} keyboardShouldPersistTaps="handled">
        {routes.map((route, index) => (
          <View key={index} style={styles.routeCard}>
            <View style={styles.routeHeader}>
              <Text style={styles.rankBadge}>#{index + 1} • Score: {route.finalScore}</Text>
              <Text style={styles.priceTag}>${route.estimatedCostUsd.toFixed(2)}</Text>
            </View>
            <Text style={styles.summary}>{route.summary}</Text>
            <Text style={styles.meta}>
              {route.durationMinutes} mins • {route.transfers} transfer(s)
            </Text>
            {route.legs.map((leg, i) => (
              <Text key={i} style={styles.legText}>
                • {leg.mode} {leg.routeShortName}: {leg.fromName} → {leg.toName} ({leg.durationMinutes}m)
              </Text>
            ))}
          </View>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F2F4F8', paddingHorizontal: 16, paddingTop: 12 },
  title: { fontSize: 22, fontWeight: '700', color: '#1A1A1A', marginBottom: 10 },
  card: { backgroundColor: '#FFF', padding: 14, borderRadius: 12 },
  inputLabel: { fontSize: 12, fontWeight: '600', color: '#666', marginBottom: 4 },
  input: {
    backgroundColor: '#F7F8FA', borderWidth: 1, borderColor: '#E1E4E8',
    borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, fontSize: 15, marginBottom: 10,
  },
  dropdown: {
    backgroundColor: '#FFF', borderWidth: 1, borderColor: '#DDD',
    borderRadius: 8, marginBottom: 10, maxHeight: 180,
  },
  dropdownItem: { padding: 10, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  dropdownTitle: { fontSize: 14, fontWeight: '600', color: '#222' },
  dropdownSub: { fontSize: 12, color: '#666' },
  label: { fontSize: 14, fontWeight: '600', textAlign: 'center', marginTop: 4 },
  sliderLabels: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  smallText: { fontSize: 12, color: '#555' },
  button: { backgroundColor: '#007AFF', padding: 13, borderRadius: 10, alignItems: 'center' },
  buttonText: { color: '#FFF', fontWeight: '700', fontSize: 16 },
  error: { color: 'red', marginTop: 12, textAlign: 'center' },
  results: { marginTop: 14 },
  routeCard: { backgroundColor: '#FFF', padding: 14, borderRadius: 12, marginBottom: 12 },
  routeHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  rankBadge: { fontWeight: '700', color: '#007AFF', fontSize: 13 },
  priceTag: { fontWeight: '700', color: '#28A745', fontSize: 16 },
  summary: { fontSize: 15, fontWeight: '600', color: '#222', marginBottom: 4 },
  meta: { fontSize: 13, color: '#666', marginBottom: 8 },
  legText: { fontSize: 12, color: '#444', marginLeft: 4, marginBottom: 2 },
});

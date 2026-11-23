import React, { useState } from 'react';
import {
  StyleSheet, Text, View, TouchableOpacity, ScrollView, ActivityIndicator
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Slider from '@react-native-community/slider';


const API_URL = 'http://IP_HERE/api/plan';

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
  const [timeWeight, setTimeWeight] = useState(0.5);
  const [routes, setRoutes] = useState<RankedItinerary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const costWeight = Math.round((1 - timeWeight) * 100) / 100;

  const findRoutes = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLat: 47.3168,
          originLon: -122.3046,
          destLat: 47.6114,
          destLon: -122.3375,
          timeWeight: timeWeight,
          costWeight: costWeight,
        }),
      });
      if (!response.ok) {
        throw new Error(`Server returned ${response.status}`);
      }
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
      <Text style={styles.title}>Seattle Multimodal Router</Text>
      <Text style={styles.subtitle}>Federal Way → Downtown Seattle</Text>

      <View style={styles.card}>
        <Text style={styles.label}>
          Priority: {Math.round(timeWeight * 100)}% Speed / {Math.round(costWeight * 100)}% Savings
        </Text>
        <Slider
          style={{ width: '100%', height: 40 }}
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
          <Text style={styles.buttonText}>Find Best Multimodal Routes</Text>
        </TouchableOpacity>
      </View>

      {loading && <ActivityIndicator size="large" color="#007AFF" style={{ marginTop: 20 }} />}
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <ScrollView style={styles.results}>
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
  container: { flex: 1, backgroundColor: '#F2F4F8', paddingHorizontal: 16, paddingTop: 16 },
  title: { fontSize: 22, fontWeight: '700', color: '#1A1A1A' },
  subtitle: { fontSize: 14, color: '#666', marginBottom: 16 },
  card: { backgroundColor: '#FFF', padding: 16, borderRadius: 12 },
  label: { fontSize: 15, fontWeight: '600', textAlign: 'center', marginBottom: 6 },
  sliderLabels: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 14 },
  smallText: { fontSize: 12, color: '#555' },
  button: { backgroundColor: '#007AFF', padding: 14, borderRadius: 10, alignItems: 'center' },
  buttonText: { color: '#FFF', fontWeight: '700', fontSize: 16 },
  error: { color: 'red', marginTop: 12, textAlign: 'center' },
  results: { marginTop: 16 },
  routeCard: { backgroundColor: '#FFF', padding: 14, borderRadius: 12, marginBottom: 12 },
  routeHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  rankBadge: { fontWeight: '700', color: '#007AFF', fontSize: 13 },
  priceTag: { fontWeight: '700', color: '#28A745', fontSize: 16 },
  summary: { fontSize: 15, fontWeight: '600', color: '#222', marginBottom: 4 },
  meta: { fontSize: 13, color: '#666', marginBottom: 8 },
  legText: { fontSize: 12, color: '#444', marginLeft: 4, marginBottom: 2 },
});

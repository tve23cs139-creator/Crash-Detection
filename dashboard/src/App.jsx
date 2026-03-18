import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from './firebase';
import { AlertTriangle, TrendingUp, AlertCircle, MapPin, Clock, Zap } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function App() {
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    try {
      const accidentsRef = ref(database, 'reported_accidents');
      
      const unsubscribe = onValue(accidentsRef, (snapshot) => {
        if (snapshot.exists()) {
          const data = snapshot.val();
          const incidentsArray = Object.entries(data).map(([key, value]) => ({
            id: key,
            ...value
          }));
          
          // Sort by timestamp descending (most recent first)
          incidentsArray.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
          setIncidents(incidentsArray);
          setError(null);
        } else {
          setIncidents([]);
        }
        setLoading(false);
      }, (error) => {
        setError(error.message);
        setLoading(false);
      });

      return () => unsubscribe();
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  }, []);

  // Calculate statistics
  const totalIncidents = incidents.length;
  const maxGForce = incidents.length > 0 
    ? Math.max(...incidents.map(i => parseFloat(i.maxG) || 0))
    : 0;
  const activeAlerts = incidents.filter(i => i.severity === 'EXTREME' || i.severity === 'SEVERE').length;

  // Prepare chart data
  const severityData = incidents.reduce((acc, incident) => {
    const severity = incident.severity || 'UNKNOWN';
    const existing = acc.find(item => item.severity === severity);
    if (existing) {
      existing.count += 1;
    } else {
      acc.push({ severity, count: 1 });
    }
    return acc;
  }, []);

  // Get last 10 incidents
  const recentIncidents = incidents.slice(0, 10);

  // Format timestamp
  const formatTime = (timestamp) => {
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  // Get Google Maps URL
  const getGoogleMapsUrl = (lat, lng) => {
    return `https://www.google.com/maps?q=${lat},${lng}`;
  };

  // Get severity color
  const getSeverityColor = (severity) => {
    switch (severity) {
      case 'EXTREME':
        return 'text-red-500 bg-red-950';
      case 'SEVERE':
        return 'text-orange-500 bg-orange-950';
      case 'MINOR':
        return 'text-yellow-500 bg-yellow-950';
      default:
        return 'text-gray-400 bg-gray-900';
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-white p-4 md:p-8">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <AlertTriangle className="w-8 h-8 text-red-500" />
          <h1 className="text-4xl font-bold">SOC Dashboard</h1>
        </div>
        <p className="text-slate-400">Security Operations Center - Crash Detection System</p>
      </div>

      {error && (
        <div className="bg-red-950 border border-red-700 text-red-200 px-4 py-3 rounded-lg mb-6">
          <p className="font-semibold">Connection Error</p>
          <p className="text-sm">{error}</p>
          <p className="text-xs mt-2">Please check your Firebase configuration in src/firebase.js</p>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-red-500 mx-auto mb-4"></div>
            <p className="text-slate-400">Loading dashboard data...</p>
          </div>
        </div>
      )}

      {!loading && (
        <>
          {/* Stats Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            {/* Total Incidents Card */}
            <div className="backdrop-blur-xl bg-white/5 border border-white/10 rounded-xl p-6 hover:bg-white/10 transition-all">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-slate-400 text-sm font-medium">Total Incidents</p>
                  <p className="text-4xl font-bold mt-2">{totalIncidents}</p>
                </div>
                <AlertCircle className="w-12 h-12 text-blue-500 opacity-80" />
              </div>
            </div>

            {/* Max G-Force Card */}
            <div className="backdrop-blur-xl bg-white/5 border border-white/10 rounded-xl p-6 hover:bg-white/10 transition-all">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-slate-400 text-sm font-medium">Max G-Force Recorded</p>
                  <p className="text-4xl font-bold mt-2">{maxGForce.toFixed(2)}G</p>
                </div>
                <TrendingUp className="w-12 h-12 text-yellow-500 opacity-80" />
              </div>
            </div>

            {/* Active Alerts Card */}
            <div className="backdrop-blur-xl bg-white/5 border border-white/10 rounded-xl p-6 hover:bg-white/10 transition-all">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-slate-400 text-sm font-medium">Active Alerts</p>
                  <p className="text-4xl font-bold mt-2 text-red-500">{activeAlerts}</p>
                </div>
                <Zap className="w-12 h-12 text-red-500 opacity-80" />
              </div>
            </div>
          </div>

          {/* Chart Row */}
          <div className="backdrop-blur-xl bg-white/5 border border-white/10 rounded-xl p-6 mb-8">
            <h2 className="text-xl font-bold mb-6">Incident Severity Distribution</h2>
            {severityData.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={severityData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                  <XAxis dataKey="severity" stroke="rgba(255,255,255,0.5)" />
                  <YAxis stroke="rgba(255,255,255,0.5)" />
                  <Tooltip 
                    contentStyle={{ 
                      backgroundColor: 'rgba(15, 23, 42, 0.8)', 
                      border: '1px solid rgba(255,255,255,0.1)',
                      borderRadius: '8px'
                    }}
                    cursor={{ fill: 'rgba(255,255,255,0.1)' }}
                  />
                  <Legend />
                  <Bar dataKey="count" fill="#ef4444" name="Incidents" radius={[8, 8, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-64 flex items-center justify-center text-slate-400">
                No data available
              </div>
            )}
          </div>

          {/* Table Row */}
          <div className="backdrop-blur-xl bg-white/5 border border-white/10 rounded-xl p-6">
            <h2 className="text-xl font-bold mb-6">Recent Incidents (Last 10)</h2>
            {recentIncidents.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-white/10">
                      <th className="text-left py-3 px-4 text-slate-400 font-semibold">Timestamp</th>
                      <th className="text-left py-3 px-4 text-slate-400 font-semibold">Severity</th>
                      <th className="text-left py-3 px-4 text-slate-400 font-semibold">Max G-Force</th>
                      <th className="text-left py-3 px-4 text-slate-400 font-semibold">Location</th>
                      <th className="text-left py-3 px-4 text-slate-400 font-semibold">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentIncidents.map((incident, index) => (
                      <tr 
                        key={incident.id} 
                        className={`border-b border-white/5 hover:bg-white/5 transition-colors ${
                          index % 2 === 0 ? 'bg-slate-900/30' : ''
                        }`}
                      >
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-2">
                            <Clock className="w-4 h-4 text-slate-500" />
                            {formatTime(incident.timestamp)}
                          </div>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${getSeverityColor(incident.severity)}`}>
                            {incident.severity}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-mono">{parseFloat(incident.maxG).toFixed(2)}G</td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-1 text-slate-300">
                            <MapPin className="w-4 h-4" />
                            <span className="text-xs">
                              {parseFloat(incident.latitude).toFixed(4)}, {parseFloat(incident.longitude).toFixed(4)}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-4">
                          <a
                            href={getGoogleMapsUrl(incident.latitude, incident.longitude)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 bg-blue-600 hover:bg-blue-700 px-3 py-1 rounded text-xs font-semibold transition-colors"
                          >
                            <MapPin className="w-3 h-3" />
                            Maps
                          </a>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="text-center py-12 text-slate-400">
                No incidents recorded yet. Waiting for data...
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="mt-8 text-center text-slate-500 text-xs">
            <p>Real-time updates enabled • Last sync: {new Date().toLocaleTimeString()}</p>
          </div>
        </>
      )}
    </div>
  );
}

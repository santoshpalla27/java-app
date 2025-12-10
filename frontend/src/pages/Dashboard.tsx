import { useEffect, useState } from 'react';
import Layout from '../components/Layout';
import DependencyGraph from '../components/dashboard/DependencyGraph';
import MetricsChart from '../components/dashboard/MetricsChart';
import FailurePanel from '../components/admin/FailurePanel';
import { useWebSocket } from '../context/WebSocketContext';
import { useAuth } from '../context/AuthContext';
import { Terminal } from 'lucide-react';

interface SystemState {
    [key: string]: 'SUCCESS' | 'FAILURE' | 'DEGRADED';
}

const Dashboard = () => {
    const { lastEvent, isConnected } = useWebSocket();
    const { user } = useAuth();

    const [systemState, setSystemState] = useState<SystemState>({
        MYSQL: 'SUCCESS',
        REDIS: 'SUCCESS',
        KAFKA: 'SUCCESS'
    });

    // Maintain a sliding window of metrics
    const [metrics, setMetrics] = useState<any[]>([]);
    const [logs, setLogs] = useState<string[]>([]);

    useEffect(() => {
        if (lastEvent) {
            // Update System Status
            setSystemState(prev => ({
                ...prev,
                [lastEvent.target]: lastEvent.status
            }));

            // Update Metrics
            setMetrics(prev => {
                const newMetric = {
                    time: new Date(lastEvent.timestamp).toLocaleTimeString(),
                    latency: lastEvent.latencyMs
                };
                const newMetrics = [...prev, newMetric];
                if (newMetrics.length > 50) return newMetrics.slice(newMetrics.length - 50);
                return newMetrics;
            });

            // Add Log
            setLogs(prev => {
                const log = `[${new Date(lastEvent.timestamp).toLocaleTimeString()}] ${lastEvent.target} -> ${lastEvent.status} (${lastEvent.latencyMs}ms)`;
                return [log, ...prev].slice(0, 10);
            });
        }
    }, [lastEvent]);

    return (
        <Layout>
            <div className="grid grid-cols-12 gap-6 h-full">
                {/* Left Column: Graph & Chaos */}
                <div className="col-span-8 space-y-6">
                    <div className="flex items-center justify-between mb-2">
                        <h2 className="text-lg font-semibold text-slate-200">System Topology</h2>
                        <div className={`px-2 py-1 rounded text-xs font-mono font-bold ${isConnected ? 'bg-green-500/20 text-green-500' : 'bg-red-500/20 text-red-500'}`}>
                            {isConnected ? 'LIVE STREAM CONNECTED' : 'DISCONNECTED'}
                        </div>
                    </div>

                    <DependencyGraph systemState={systemState} />

                    {user?.role === 'ADMIN' && (
                        <div>
                            <h2 className="text-lg font-semibold text-slate-200 mb-4">Admin Controls</h2>
                            <FailurePanel />
                        </div>
                    )}
                </div>

                {/* Right Column: Metrics & Logs */}
                <div className="col-span-4 space-y-6">
                    <div>
                        <h2 className="text-lg font-semibold text-slate-200 mb-4">Live Latency</h2>
                        <MetricsChart data={metrics} />
                    </div>

                    <div className="bg-slate-900 border border-slate-800 rounded-lg p-4 h-[300px] flex flex-col">
                        <h3 className="text-sm font-medium text-slate-400 mb-2 flex items-center gap-2">
                            <Terminal className="w-4 h-4" /> Event Stream
                        </h3>
                        <div className="flex-1 overflow-auto font-mono text-xs space-y-1">
                            {logs.map((log, i) => (
                                <div key={i} className={`p-1 border-b border-slate-800 ${log.includes('FAILURE') ? 'text-red-400' : 'text-slate-500'}`}>
                                    {log}
                                </div>
                            ))}
                            {logs.length === 0 && <span className="text-slate-600">Waiting for events...</span>}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
};

export default Dashboard;

import { useState } from 'react';
import api from '../../api/client';
import { Skull, Database, Flame, Clock, Activity } from 'lucide-react';

const FailurePanel = () => {
    const [, setLoading] = useState(false);

    const triggerUserFailure = async (type: string, value?: number) => {
        setLoading(true);
        try {
            if (type === 'latency') {
                await api.post(`/admin/failure/latency/${value}`);
            } else if (type === 'db-kill') {
                await api.post('/admin/failure/db/kill');
            } else if (type === 'db-exhaust') {
                await api.post('/admin/failure/db/exhaust');
            } else if (type === 'redis-flush') {
                await api.post('/admin/failure/redis/flush');
            }
        } catch (e) {
            console.error(e);
        }
        setLoading(false);
    };

    return (
        <div className="bg-slate-900/50 p-6 rounded-lg border border-slate-800">
            <h3 className="text-sm font-medium text-red-400 mb-4 flex items-center gap-2">
                <Skull className="w-4 h-4" /> Chaos Engineering
            </h3>
            <div className="grid grid-cols-2 gap-4">
                <button onClick={() => triggerUserFailure('db-kill')}
                    className="flex flex-col items-center justify-center p-4 bg-red-950 hover:bg-red-900 border border-red-900 rounded-lg transition-colors gap-2">
                    <Database className="w-6 h-6 text-red-500" />
                    <span className="text-xs font-mono text-red-200">KILL DB CONNS</span>
                </button>
                <button onClick={() => triggerUserFailure('redis-flush')}
                    className="flex flex-col items-center justify-center p-4 bg-orange-950 hover:bg-orange-900 border border-orange-900 rounded-lg transition-colors gap-2">
                    <Flame className="w-6 h-6 text-orange-500" />
                    <span className="text-xs font-mono text-orange-200">FLUSH REDIS</span>
                </button>
                <button onClick={() => triggerUserFailure('latency', 2000)}
                    className="flex flex-col items-center justify-center p-4 bg-yellow-950 hover:bg-yellow-900 border border-yellow-900 rounded-lg transition-colors gap-2">
                    <Clock className="w-6 h-6 text-yellow-500" />
                    <span className="text-xs font-mono text-yellow-200">ADD LATENCY</span>
                </button>
                <button onClick={() => triggerUserFailure('latency', 0)}
                    className="flex flex-col items-center justify-center p-4 bg-green-950 hover:bg-green-900 border border-green-900 rounded-lg transition-colors gap-2">
                    <Activity className="w-6 h-6 text-green-500" />
                    <span className="text-xs font-mono text-green-200">RESET SYSTEM</span>
                </button>
            </div>
        </div>
    );
};

export default FailurePanel;

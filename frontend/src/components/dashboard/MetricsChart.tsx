import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface Props {
    data: any[];
}

const MetricsChart: React.FC<Props> = ({ data }) => {
    return (
        <div className="h-[300px] w-full bg-slate-900/50 rounded-lg border border-slate-800 p-4">
            <h3 className="text-sm font-medium text-slate-400 mb-4">System Latency (ms)</h3>
            <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis dataKey="time" hide />
                    <YAxis stroke="#475569" fontSize={12} />
                    <Tooltip
                        contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b' }}
                    />
                    <Area type="monotone" dataKey="latency" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.1} />
                </AreaChart>
            </ResponsiveContainer>
        </div>
    );
};

export default MetricsChart;

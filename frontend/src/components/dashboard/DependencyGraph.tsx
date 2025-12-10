import React from 'react';
import ReactFlow, { Background, Controls, Node, Edge } from 'reactflow';
import 'reactflow/dist/style.css';

interface Props {
    systemState: Record<string, 'SUCCESS' | 'FAILURE' | 'DEGRADED'>;
}

const DependencyGraph: React.FC<Props> = ({ systemState }) => {



    // Nodes
    const nodes: Node[] = React.useMemo(() => [
        {
            id: 'browser',
            type: 'input',
            data: { label: 'Browser' },
            position: { x: 50, y: 150 },
            style: { background: '#1e293b', color: '#fff', border: '1px solid #475569', width: 120 }
        },
        {
            id: 'backend',
            data: { label: 'Spring Boot' },
            position: { x: 250, y: 150 },
            style: { background: '#3b82f6', color: '#fff', border: '1px solid #2563eb', width: 140 }
        },
        {
            id: 'mysql',
            data: { label: 'MySQL (RDS)' },
            position: { x: 500, y: 50 },
            style: {
                background: systemState['MYSQL'] === 'FAILURE' ? '#7f1d1d' : '#0f172a',
                color: '#fff',
                border: systemState['MYSQL'] === 'FAILURE' ? '2px solid #ef4444' : '1px solid #334155', width: 120
            }
        },
        {
            id: 'redis',
            data: { label: 'Redis' },
            position: { x: 500, y: 150 },
            style: {
                background: systemState['REDIS'] === 'FAILURE' ? '#7f1d1d' : '#0f172a',
                color: '#fff',
                border: systemState['REDIS'] === 'FAILURE' ? '2px solid #ef4444' : '1px solid #334155', width: 120
            }
        },
        {
            id: 'kafka',
            data: { label: 'Kafka' },
            position: { x: 500, y: 250 },
            style: {
                background: systemState['KAFKA'] === 'FAILURE' ? '#7f1d1d' : '#0f172a',
                color: '#fff',
                border: systemState['KAFKA'] === 'FAILURE' ? '2px solid #ef4444' : '1px solid #334155', width: 120
            }
        }
    ], [systemState]);

    // Edges
    const edges: Edge[] = React.useMemo(() => [
        { id: 'e1-2', source: 'browser', target: 'backend', animated: true, style: { stroke: '#94a3b8' } },
        {
            id: 'e2-3', source: 'backend', target: 'mysql', animated: true,
            style: { stroke: systemState['MYSQL'] === 'FAILURE' ? '#ef4444' : '#10b981', strokeWidth: 2 }
        },
        {
            id: 'e2-4', source: 'backend', target: 'redis', animated: true,
            style: { stroke: systemState['REDIS'] === 'FAILURE' ? '#ef4444' : '#10b981', strokeWidth: 2 }
        },
        {
            id: 'e2-5', source: 'backend', target: 'kafka', animated: true,
            style: { stroke: systemState['KAFKA'] === 'FAILURE' ? '#ef4444' : '#10b981', strokeWidth: 2 }
        },
    ], [systemState]);

    return (
        <div className="h-[350px] w-full bg-slate-900/50 rounded-lg border border-slate-800">
            <ReactFlow nodes={nodes} edges={edges} fitView>
                <Background color="#334155" gap={20} />
                <Controls className="bg-slate-800 text-white border-slate-700" />
            </ReactFlow>
        </div>
    );
};

export default DependencyGraph;

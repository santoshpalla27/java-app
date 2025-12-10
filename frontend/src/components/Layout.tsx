import React from 'react';
import { useAuth } from '../context/AuthContext';
import { LogOut, Activity } from 'lucide-react';

const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const { user, logout } = useAuth();

    return (
        <div className="min-h-screen bg-slate-950 flex flex-col">
            <header className="bg-slate-900 border-b border-slate-800 px-6 py-4 flex justify-between items-center">
                <div className="flex items-center gap-3">
                    <Activity className="w-6 h-6 text-green-500" />
                    <h1 className="text-xl font-bold bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
                        SysBehavior <span className="text-xs font-mono text-slate-500 bg-slate-800 px-1 py-0.5 rounded ml-2">PROD</span>
                    </h1>
                </div>
                <div className="flex items-center gap-4">
                    <div className="text-sm text-slate-400">
                        User: <span className="text-white font-medium">{user?.username}</span>
                    </div>
                    <button onClick={logout} className="p-2 hover:bg-slate-800 rounded-full text-slate-400 hover:text-white transition-colors">
                        <LogOut className="w-5 h-5" />
                    </button>
                </div>
            </header>
            <main className="flex-1 overflow-auto p-6">
                {children}
            </main>
        </div>
    );
};

export default Layout;

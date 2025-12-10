import React, { createContext, useContext, useEffect, useState } from 'react';
import Stomp from 'stompjs';
import SockJS from 'sockjs-client';

interface WebSocketContextType {
    lastEvent: any;
    isConnected: boolean;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [lastEvent, setLastEvent] = useState<any>(null);
    const [isConnected, setIsConnected] = useState(false);

    useEffect(() => {
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            setIsConnected(true);

            stompClient.subscribe('/topic/events', (message) => {
                if (message.body) {
                    const event = JSON.parse(message.body);
                    setLastEvent(event);
                }
            });
        }, (error) => {
            console.error('STOMP error', error);
            setIsConnected(false);
        });

        return () => {
            if (stompClient) stompClient.disconnect(() => { });
        };
    }, []);

    return (
        <WebSocketContext.Provider value={{ lastEvent, isConnected }}>
            {children}
        </WebSocketContext.Provider>
    );
};

export const useWebSocket = () => {
    const context = useContext(WebSocketContext);
    if (!context) throw new Error('useWebSocket must be used within a WebSocketProvider');
    return context;
};

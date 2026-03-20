export interface ServiceRegion {
    code: string;
    name: string;
    url: string;
}

export interface Service {
    id: string;
    name: string;
    url: string;
    icon: string;
    type: 'default' | 'custom';
    regions?: ServiceRegion[];
}

export interface HistoryItem {
    path: string;
    url: string;
    timestamp: number;
    filename: string;
}

export interface SnapseekAPI {
    openService: (serviceName: string) => Promise<void>;
    closeService: () => Promise<void>;
    goBack: () => Promise<void>;
    downloadImage: (imageUrl: string, format: string) => Promise<{ success: boolean; path?: string; error?: string }>;
    getDownloadPath: () => Promise<string>;
    setDownloadPath: () => Promise<{ success: boolean; path?: string }>;
    openSettings: () => Promise<void>;
    openHistory: () => Promise<void>;
    closeSettings: () => Promise<void>;
    onShowNavBar: (callback: () => void) => void;
    onHideNavBar: (callback: () => void) => void;
    minimize: () => Promise<void>;
    toggleMaximize: () => Promise<void>;
    close: () => Promise<void>;
    getServiceStates: () => Promise<Record<string, boolean>>;
    toggleServiceState: (serviceName: string, state: boolean) => Promise<Record<string, boolean>>;
    getAllServices: () => Promise<Service[]>;
    addCustomService: (serviceData: { name: string; url: string; iconUrl?: string }) => Promise<Service[]>;
    removeCustomService: (serviceId: string) => Promise<Service[]>;
    getDarkModeState: () => Promise<boolean>;
    toggleDarkModeState: (state: boolean) => Promise<boolean>;
    openDownloadsFolder: () => Promise<boolean>;
    getDownloadHistory: () => Promise<HistoryItem[]>;
    clearDownloadHistory: () => Promise<boolean>;
    updateServiceUrl: (serviceId: string, newUrl: string) => Promise<boolean>;
    onNavigate: (callback: (event: any, route: string) => void) => void;
}

declare global {
    interface Window {
        snapseek: SnapseekAPI;
        openService?: (serviceId: string) => void;
        updateServiceRegion?: (serviceId: string, newUrl: string) => void;
        openFile?: (path: string) => void;
    }
}

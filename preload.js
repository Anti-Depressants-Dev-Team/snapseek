const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process to use
// the ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('snapseek', {
    openService: (serviceName) => ipcRenderer.invoke('open-service', serviceName),
    closeService: () => ipcRenderer.invoke('close-service'),
    goBack: () => ipcRenderer.invoke('go-back'),
    downloadImage: (imageUrl, format) => ipcRenderer.invoke('download-image', imageUrl, format),
    getDownloadPath: () => ipcRenderer.invoke('get-download-path'),
    setDownloadPath: () => ipcRenderer.invoke('set-download-path'),
    openSettings: () => ipcRenderer.invoke('open-settings'),
    openHistory: () => ipcRenderer.invoke('open-history'),
    closeSettings: () => ipcRenderer.invoke('close-settings'),
    onShowNavBar: (callback) => ipcRenderer.on('show-nav-bar', callback),
    onHideNavBar: (callback) => ipcRenderer.on('hide-nav-bar', callback),
    minimize: () => ipcRenderer.invoke('window-minimize'),
    toggleMaximize: () => ipcRenderer.invoke('window-maximize'),
    close: () => ipcRenderer.invoke('window-close'),
    getServiceStates: () => ipcRenderer.invoke('get-service-states'),
    toggleServiceState: (serviceName, state) => ipcRenderer.invoke('toggle-service-state', serviceName, state),
    getAllServices: () => ipcRenderer.invoke('get-all-services'),
    addCustomService: (serviceData) => ipcRenderer.invoke('add-custom-service', serviceData),
    removeCustomService: (serviceId) => ipcRenderer.invoke('remove-custom-service', serviceId),
    getDarkModeState: () => ipcRenderer.invoke('get-dark-mode-state'),
    toggleDarkModeState: (state) => ipcRenderer.invoke('toggle-dark-mode-state', state),

    // History & Folder
    openDownloadsFolder: () => ipcRenderer.invoke('open-downloads-folder'),
    getDownloadHistory: () => ipcRenderer.invoke('get-download-history'),
    clearDownloadHistory: () => ipcRenderer.invoke('clear-download-history'),

    // Services
    updateServiceUrl: (serviceId, newUrl) => ipcRenderer.invoke('update-service-url', serviceId, newUrl)
});

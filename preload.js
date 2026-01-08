const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process to use
// the ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('snapseek', {
    openService: (serviceName) => ipcRenderer.invoke('open-service', serviceName),
    closeService: () => ipcRenderer.invoke('close-service'),
    goBack: () => ipcRenderer.invoke('go-back'),
    downloadImage: (imageUrl, filename) => ipcRenderer.invoke('download-image', imageUrl, filename),
    getDownloadPath: () => ipcRenderer.invoke('get-download-path'),
    setDownloadPath: () => ipcRenderer.invoke('set-download-path'),
    openSettings: () => ipcRenderer.invoke('open-settings'),
    closeSettings: () => ipcRenderer.invoke('close-settings'),
    onShowNavBar: (callback) => ipcRenderer.on('show-nav-bar', callback),
    onHideNavBar: (callback) => ipcRenderer.on('hide-nav-bar', callback),
    minimize: () => ipcRenderer.invoke('window-minimize'),
    toggleMaximize: () => ipcRenderer.invoke('window-maximize'),
    close: () => ipcRenderer.invoke('window-close')
});

const { app, BrowserWindow, BrowserView, ipcMain, dialog, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

if (process.platform === 'win32') {
  app.setAppUserModelId('com.yabosen.snapseek');
}
const sharp = require('sharp');
const Store = require('electron-store');
const { ElectronBlocker } = require('@cliqz/adblocker-electron');
const fetch = require('cross-fetch');

const store = new Store();
let mainWindow;
let currentView = null;
let blocker = null;

// Default download directory
const defaultDownloadPath = app.getPath('downloads');

// Initialize ad blocker
async function initializeAdBlocker() {
  blocker = await ElectronBlocker.fromPrebuiltAdsAndTracking(fetch);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    backgroundColor: '#000000',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    },
    icon: path.resolve(__dirname, 'icon.ico'),
    frame: false // Custom Title Bar
  });

  mainWindow.loadFile('index.html');

  // Remove menu bar for cleaner look
  mainWindow.setMenuBarVisibility(false);
}

function createBrowserView(url) {
  // Remove existing view if any
  if (currentView) {
    mainWindow.removeBrowserView(currentView);
    currentView.webContents.destroy();
  }

  // Create new BrowserView
  currentView = new BrowserView({
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js'),
      webSecurity: false // nuclear option for image loading
    }
  });

  // Ensure background is opaque white (prevents bleed-through if site has transparent background)
  currentView.setBackgroundColor('#ffffff');

  // Set User Agent to standard Chrome on Windows to prevent site blocking/lazy-loading issues
  // Using Chrome 124 to ensure maximum compatibility
  currentView.webContents.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36');

  // Fix for anti-hotlinking: Inject Referer headers
  // Pinterest and Pixiv images often return 403 Forbidden if the Referer header is missing or incorrect
  currentView.webContents.session.webRequest.onBeforeSendHeaders(
    { urls: ['*://*.pinimg.com/*', '*://*.pinterest.com/*', '*://*.pixiv.net/*', '*://*.pximg.net/*'] },
    (details, callback) => {
      const { requestHeaders } = details;
      const url = new URL(details.url);

      if (url.hostname.includes('pinterest') || url.hostname.includes('pinimg')) {
        requestHeaders['Referer'] = 'https://www.pinterest.com/';
        requestHeaders['Origin'] = 'https://www.pinterest.com';
      } else if (url.hostname.includes('pixiv') || url.hostname.includes('pximg')) {
        requestHeaders['Referer'] = 'https://www.pixiv.net/';
        requestHeaders['Origin'] = 'https://www.pixiv.net';
      }

      callback({ requestHeaders });
    }
  );

  mainWindow.addBrowserView(currentView);

  // Set bounds (leave space for navigation bar at top: 40px)
  const bounds = mainWindow.getContentBounds();
  currentView.setBounds({
    x: 0,
    y: 40,
    width: bounds.width,
    height: bounds.height - 40
  });

  currentView.setAutoResize({
    width: true,
    height: true
  });

  // Load the URL
  currentView.webContents.loadURL(url);

  // Inject content script after page loads
  currentView.webContents.on('did-finish-load', () => {
    // Nuclear option: Unregister all service workers to force fresh network requests
    currentView.webContents.executeJavaScript(`
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.getRegistrations().then(function(registrations) {
          for(let registration of registrations) {
            console.log('SnapSeek: Unregistering SW:', registration);
            registration.unregister();
          }
        });
      }
    `);

    // Inject Dark Reader first
    const darkReaderPath = path.join(__dirname, 'node_modules', 'darkreader', 'darkreader.js');
    if (fs.existsSync(darkReaderPath)) {
      const darkReaderScript = fs.readFileSync(darkReaderPath, 'utf8');
      currentView.webContents.executeJavaScript(darkReaderScript).then(() => {
        // Inject custom dark mode script
        const darkModeScript = fs.readFileSync(path.join(__dirname, 'darkmode.js'), 'utf8');
        currentView.webContents.executeJavaScript(darkModeScript);
      });
    }

    // Inject download button overlay script
    const injectScript = fs.readFileSync(path.join(__dirname, 'inject.js'), 'utf8');
    currentView.webContents.executeJavaScript(injectScript);
  });

  // Handle navigation
  currentView.webContents.on('did-navigate', () => {
    const darkReaderPath = path.join(__dirname, 'node_modules', 'darkreader', 'darkreader.js');
    if (fs.existsSync(darkReaderPath)) {
      const darkReaderScript = fs.readFileSync(darkReaderPath, 'utf8');
      currentView.webContents.executeJavaScript(darkReaderScript).then(() => {
        const darkModeScript = fs.readFileSync(path.join(__dirname, 'darkmode.js'), 'utf8');
        currentView.webContents.executeJavaScript(darkModeScript);
      });
    }
    const injectScript = fs.readFileSync(path.join(__dirname, 'inject.js'), 'utf8');
    currentView.webContents.executeJavaScript(injectScript);

    // Context Menu for Downloading Images
    currentView.webContents.on('context-menu', (event, params) => {
      if (params.mediaType === 'image' && params.srcURL) {
        const menu = Menu.buildFromTemplate([
          {
            label: 'Download as PNG (Default)',
            click: () => {
              downloadImage(params.srcURL, 'png');
            }
          },
          {
            label: 'Download as JPG',
            click: () => {
              downloadImage(params.srcURL, 'jpg');
            }
          },
          {
            label: 'Download as JPEG',
            click: () => {
              downloadImage(params.srcURL, 'jpeg');
            }
          },
          {
            label: 'Download as GIF',
            click: () => {
              downloadImage(params.srcURL, 'gif');
            }
          }
        ]);
        menu.popup(currentView.webContents);
      }
    });
  });

  // Send message to main window to show navigation bar (controls)
  mainWindow.webContents.send('show-nav-bar');
}

function closeBrowserView() {
  if (currentView) {
    mainWindow.removeBrowserView(currentView);
    currentView.webContents.destroy();
    currentView = null;
  }
  // Send message to main window to hide navigation bar
  mainWindow.webContents.send('hide-nav-bar');
}

// IPC Handlers
// Default Services Configuration
const DEFAULT_SERVICES = [
  { id: 'pinterest', name: 'Pinterest', url: 'https://ru.pinterest.com/', icon: 'pinterest', type: 'default' },
  { id: 'safebooru', name: 'Safebooru', url: 'https://safebooru.org/', icon: 'safebooru', type: 'default' },
  { id: 'pixiv', name: 'Pixiv', url: 'https://www.pixiv.net/', icon: 'pixiv', type: 'default' },
  { id: 'wallpapers', name: 'Wallpapers', url: 'https://wallpapers.com/', icon: 'wallpapers', type: 'default' }
];

// Helper to get all services
function getAllServices() {
  const customServices = store.get('customServices', []);
  return [...DEFAULT_SERVICES, ...customServices];
}

ipcMain.handle('open-service', (event, serviceId) => {
  const services = getAllServices();
  const service = services.find(s => s.id === serviceId);

  if (service && service.url) {
    createBrowserView(service.url);
  }
});

// Service Management IPC
ipcMain.handle('get-all-services', () => {
  return getAllServices();
});

ipcMain.handle('add-custom-service', (event, serviceData) => {
  const customServices = store.get('customServices', []);
  const newService = {
    id: `custom_${Date.now()}`,
    name: serviceData.name,
    url: serviceData.url,
    icon: serviceData.iconUrl || 'default', // We'll handle icon rendering in frontend
    type: 'custom'
  };
  customServices.push(newService);
  store.set('customServices', customServices);

  // Also init its state to enabled by default
  const states = store.get('serviceStates', {});
  states[newService.id] = true;
  store.set('serviceStates', states);

  return getAllServices();
});

ipcMain.handle('remove-custom-service', (event, serviceId) => {
  let customServices = store.get('customServices', []);
  customServices = customServices.filter(s => s.id !== serviceId);
  store.set('customServices', customServices);
  return getAllServices();
});

ipcMain.handle('close-service', () => {
  closeBrowserView();
});

ipcMain.handle('go-back', () => {
  if (currentView && currentView.webContents && currentView.webContents.canGoBack()) {
    currentView.webContents.goBack();
  }
});

// Window Controls
ipcMain.handle('window-minimize', () => {
  mainWindow.minimize();
});

ipcMain.handle('window-maximize', () => {
  if (mainWindow.isMaximized()) {
    mainWindow.unmaximize();
  } else {
    mainWindow.maximize();
  }
});

ipcMain.handle('window-close', () => {
  mainWindow.close();
});

// Standalone download function for Context Menu and IPC
async function downloadImage(imageUrl, format = 'png') {
  try {
    const downloadPath = store.get('downloadPath', defaultDownloadPath);

    // Ensure download directory exists
    if (!fs.existsSync(downloadPath)) {
      fs.mkdirSync(downloadPath, { recursive: true });
    }

    // Generate random hash-like filename (32 chars like MD5)
    // User requested random string
    const crypto = require('crypto');
    const randomHash = crypto.randomBytes(16).toString('hex');
    let filename = randomHash;

    // Normalize format
    format = format.toLowerCase();
    if (format === 'jpeg') format = 'jpg';

    let extension = format;
    if (extension === 'jpg') extension = 'jpg'; // Explicitly set for clarity

    let outputPath = path.join(downloadPath, `${filename}.${extension}`);

    // Check for duplicates
    let counter = 2; // User requested starts with (2)
    while (fs.existsSync(outputPath)) {
      // User requested: if there are 2 of the same names just add a (2)
      // If "filename.png" exists, next is "filename (2).png"
      // If "filename (2).png" exists, next is "filename (3).png"
      outputPath = path.join(downloadPath, `${filename} (${counter}).${extension}`);
      counter++;
    }

    // Download image
    const response = await fetch(imageUrl);
    if (!response.ok) {
      throw new Error(`Failed to download image: ${response.statusText}`);
    }

    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // Convert using Sharp
    let pipeline = sharp(buffer);

    if (format === 'png') {
      pipeline = pipeline.png();
    } else if (format === 'jpg' || format === 'jpeg') {
      pipeline = pipeline.jpeg({ quality: 90 });
    } else if (format === 'gif') {
      // Sharp GIF support requires libvips with gif support. 
      // Simple GIF output:
      pipeline = pipeline.gif();
    }

    await pipeline.toFile(outputPath);

    // Notify user via console or success
    console.log(`SnapSeek: Downloaded to ${outputPath}`);

    // Optional: Flash the taskbar or show a notification?
    // For now, silently succeed as requested functionality is just core logic.

    return { success: true, path: outputPath };
  } catch (error) {
    console.error('Download error:', error);
    return { success: false, error: error.message };
  }
}

ipcMain.handle('download-image', async (event, imageUrl, format = 'png') => {
  return await downloadImage(imageUrl, format);
});

ipcMain.handle('get-download-path', () => {
  return store.get('downloadPath', defaultDownloadPath);
});

ipcMain.handle('set-download-path', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory']
  });

  if (!result.canceled && result.filePaths.length > 0) {
    const selectedPath = result.filePaths[0];
    store.set('downloadPath', selectedPath);
    return { success: true, path: selectedPath };
  }

  return { success: false };
});

ipcMain.handle('open-settings', () => {
  mainWindow.loadFile('settings.html');
  closeBrowserView();
});

ipcMain.handle('close-settings', () => {
  mainWindow.loadFile('index.html');
});

// Service State Management
ipcMain.handle('get-service-states', () => {
  const defaultStates = {};
  getAllServices().forEach(s => {
    // Default enabled except wallpapers if that was the preference, but let's just make all enabled by default for simplicity or check store
    defaultStates[s.id] = true;
  });
  // Wallpapers defaults to false in legacy, let's respect current store or default

  return store.get('serviceStates', defaultStates);
});

ipcMain.handle('toggle-service-state', (event, serviceName, state) => {
  const currentStates = store.get('serviceStates', {});
  currentStates[serviceName] = state;
  store.set('serviceStates', currentStates);
  return currentStates;
});

// App lifecycle
app.whenReady().then(async () => {
  // await initializeAdBlocker(); // Disabled
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// Handle window resize - update BrowserView bounds
app.on('browser-window-resize', () => {
  if (currentView && mainWindow) {
    const bounds = mainWindow.getContentBounds();
    currentView.setBounds({
      x: 0,
      y: 40,
      width: bounds.width,
      height: bounds.height - 40
    });
  }
});

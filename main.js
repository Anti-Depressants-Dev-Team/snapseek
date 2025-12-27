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
    icon: path.resolve(__dirname, 'icon.ico')
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

  // Set bounds (leave space for navigation bar at top: 60px)
  const bounds = mainWindow.getContentBounds();
  currentView.setBounds({
    x: 0,
    y: 60,
    width: bounds.width,
    height: bounds.height - 60
  });

  currentView.setAutoResize({
    width: true,
    height: true
  });

  // Apply ad blocker to this view
  // AdBlocker disabled to fix image loading issues
  // if (blocker) { ... }

  // Load the URL
  currentView.webContents.loadURL(url);

  // Inject content script after page loads
  currentView.webContents.on('did-finish-load', () => {
    // Nuclear option: Unregister all service workers to force fresh network requests
    // This fixes issues where broken/403 responses are cached
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
    const darkReaderScript = fs.readFileSync(darkReaderPath, 'utf8');
    currentView.webContents.executeJavaScript(darkReaderScript).then(() => {
      // Inject custom dark mode script
      const darkModeScript = fs.readFileSync(path.join(__dirname, 'darkmode.js'), 'utf8');
      currentView.webContents.executeJavaScript(darkModeScript);
    });

    // Inject download button overlay script
    const injectScript = fs.readFileSync(path.join(__dirname, 'inject.js'), 'utf8');
    currentView.webContents.executeJavaScript(injectScript);
  });

  // Handle navigation - keep injecting script on new pages
  currentView.webContents.on('did-navigate', () => {
    // Inject Dark Reader first
    const darkReaderPath = path.join(__dirname, 'node_modules', 'darkreader', 'darkreader.js');
    const darkReaderScript = fs.readFileSync(darkReaderPath, 'utf8');
    currentView.webContents.executeJavaScript(darkReaderScript).then(() => {
      // Inject custom dark mode script
      const darkModeScript = fs.readFileSync(path.join(__dirname, 'darkmode.js'), 'utf8');
      currentView.webContents.executeJavaScript(darkModeScript);
    });

    // Inject download button overlay script
    const injectScript = fs.readFileSync(path.join(__dirname, 'inject.js'), 'utf8');
    currentView.webContents.executeJavaScript(injectScript);

    // Context Menu for Downloading Images
    currentView.webContents.on('context-menu', (event, params) => {
      if (params.mediaType === 'image' && params.srcURL) {
        const menu = Menu.buildFromTemplate([
          {
            label: 'Download Image',
            click: () => {
              downloadImage(params.srcURL); // Call internal helper
            }
          }
        ]);
        menu.popup(currentView.webContents);
      }
    });
  });

  // Send message to main window to show navigation bar
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
ipcMain.handle('open-service', (event, serviceName) => {
  const serviceUrls = {
    pinterest: 'https://ru.pinterest.com/',
    safebooru: 'https://safebooru.org/',
    pixiv: 'https://www.pixiv.net/'
  };

  const url = serviceUrls[serviceName];
  if (url) {
    createBrowserView(url);
  }
});

ipcMain.handle('close-service', () => {
  closeBrowserView();
});

// Standalone download function for Context Menu and IPC
async function downloadImage(imageUrl) {
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
    let outputPath = path.join(downloadPath, `${filename}.png`);

    // Check for duplicates
    let counter = 1;
    while (fs.existsSync(outputPath)) {
      filename = `${randomHash} (${counter})`;
      outputPath = path.join(downloadPath, `${filename}.png`);
      counter++;
    }

    // Download image
    const response = await fetch(imageUrl);
    if (!response.ok) {
      // Fallback for some headers?
      // Maybe try electron net?
      // But fetch should work if we disable webSecurity
      throw new Error(`Failed to download image: ${response.statusText}`);
    }

    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // Convert to PNG using Sharp
    await sharp(buffer)
      .png()
      .toFile(outputPath);

    // Notify user via console or success
    console.log(`SnapSeek: Downloaded to ${outputPath}`);

    return { success: true, path: outputPath };
  } catch (error) {
    console.error('Download error:', error);
    return { success: false, error: error.message };
  }
}

ipcMain.handle('download-image', async (event, imageUrl, originalFilename) => {
  return await downloadImage(imageUrl);
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
      y: 60,
      width: bounds.width,
      height: bounds.height - 60
    });
  }
});

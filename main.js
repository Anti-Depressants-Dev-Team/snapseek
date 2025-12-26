const { app, BrowserWindow, BrowserView, ipcMain, dialog } = require('electron');
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
      preload: path.join(__dirname, 'preload.js')
    }
  });

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
  if (blocker) {
    blocker.enableBlockingInSession(currentView.webContents.session);
  }

  // Load the URL
  currentView.webContents.loadURL(url);

  // Inject content script after page loads
  currentView.webContents.on('did-finish-load', () => {
    // Inject Dark Reader first
    const darkReaderPath = path.join(__dirname, 'node_modules', 'darkreader', 'darkreader.js');
    const darkReaderScript = fs.readFileSync(darkReaderPath, 'utf8');
    currentView.webContents.executeJavaScript(darkReaderScript).then(() => {
      // Enable Dark Reader with custom config
      const darkModeConfig = `
        if (window.DarkReader) {
          DarkReader.enable({
            brightness: 100,
            contrast: 100,
            sepia: 0,
            grayscale: 0,
            darkSchemeBackgroundColor: '#181a1b',
            darkSchemeTextColor: '#e8e6e3'
          });
          console.log('SnapSeek: Dark Reader enabled');
        }
      `;
      currentView.webContents.executeJavaScript(darkModeConfig);
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
      // Enable Dark Reader with custom config
      const darkModeConfig = `
        if (window.DarkReader) {
          DarkReader.enable({
            brightness: 100,
            contrast: 100,
            sepia: 0,
            grayscale: 0,
            darkSchemeBackgroundColor: '#181a1b',
            darkSchemeTextColor: '#e8e6e3'
          });
          console.log('SnapSeek: Dark Reader enabled');
        }
      `;
      currentView.webContents.executeJavaScript(darkModeConfig);
    });

    // Inject download button overlay script
    const injectScript = fs.readFileSync(path.join(__dirname, 'inject.js'), 'utf8');
    currentView.webContents.executeJavaScript(injectScript);
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

ipcMain.handle('download-image', async (event, imageUrl, originalFilename) => {
  try {
    const downloadPath = store.get('downloadPath', defaultDownloadPath);

    // Ensure download directory exists
    if (!fs.existsSync(downloadPath)) {
      fs.mkdirSync(downloadPath, { recursive: true });
    }

    // Generate random hash-like filename (32 chars like MD5)
    const crypto = require('crypto');
    const randomHash = crypto.randomBytes(16).toString('hex');
    let filename = randomHash;
    let outputPath = path.join(downloadPath, `${filename}.png`);

    // Check for duplicates and add (1), (2), etc. if needed
    let counter = 1;
    while (fs.existsSync(outputPath)) {
      filename = `${randomHash} (${counter})`;
      outputPath = path.join(downloadPath, `${filename}.png`);
      counter++;
    }

    // Download image
    const response = await fetch(imageUrl);
    if (!response.ok) {
      throw new Error(`Failed to download image: ${response.statusText}`);
    }

    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // Convert to PNG using Sharp
    await sharp(buffer)
      .png()
      .toFile(outputPath);

    return { success: true, path: outputPath };
  } catch (error) {
    console.error('Download error:', error);
    return { success: false, error: error.message };
  }
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
  await initializeAdBlocker();
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

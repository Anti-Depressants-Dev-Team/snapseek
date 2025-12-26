// Dark Reader injection for automatic dark mode
// This script applies Dark Reader to make all websites display in dark mode

(function () {
    'use strict';

    // Avoid running multiple times
    if (window._darkReaderInjected) return;
    window._darkReaderInjected = true;

    // Dark Reader configuration optimized for image browsing sites
    const darkReaderConfig = {
        brightness: 100,
        contrast: 100,
        sepia: 0,
        grayscale: 0,
        mode: 1, // Dynamic mode
        darkSchemeBackgroundColor: '#181a1b',
        darkSchemeTextColor: '#e8e6e3',
        lightSchemeBackgroundColor: '#ffffff',
        lightSchemeTextColor: '#000000',
    };

    // Import and enable Dark Reader
    // Note: This will be loaded from the node_modules via Electron's main process
    if (window.DarkReader) {
        window.DarkReader.enable(darkReaderConfig);
        console.log('SnapSeek: Dark Reader enabled');
    }
})();

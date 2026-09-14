// Dark Reader injection for automatic dark mode
// This script applies Dark Reader to make all websites display in dark mode

(function () {
    'use strict';

    // Avoid running multiple times
    if (window._darkReaderInjected) return;
    window._darkReaderInjected = true;

    const hostname = window.location.hostname;

    // Dark Mode is now safe to enable thanks to robust image persistence in inject.js
    // if (hostname.includes('pinterest') || hostname.includes('pixvin') || hostname.includes('pixiv')) { ... }

    // Default configuration
    let config = {
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

    // Site-specific fixes (Legacy/Unused if returned early)
    // Kept structure for potential future use or other sites

    // Import and enable Dark Reader
    // Note: This will be loaded from the node_modules via Electron's main process
    if (window.DarkReader) {
        // Apply fixes if defined
        if (config.fixes && config.fixes.css) {
            // We can inject custom CSS via DarkReader if needed, or just append a style tag
            const style = document.createElement('style');
            style.textContent = config.fixes.css;
            document.head.appendChild(style);
        }

        window.DarkReader.enable(config);
        console.log(`SnapSeek: Dark Reader enabled for ${hostname} with mode ${config.mode}`);
    }
})();

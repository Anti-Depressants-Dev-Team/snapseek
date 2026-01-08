// Main renderer process for the service selector
document.addEventListener('DOMContentLoaded', () => {
    const serviceCards = document.querySelectorAll('.service-card');
    const settingsBtn = document.getElementById('settings-btn');
    const backBtn = document.getElementById('back-btn');
    const navControls = document.getElementById('nav-controls');
    const browserBackBtn = document.getElementById('browser-back-btn');

    console.log('SnapSeek: Renderer loaded');

    // Handle service selection
    serviceCards.forEach(card => {
        card.addEventListener('click', async () => {
            const serviceName = card.dataset.service;
            console.log('SnapSeek: Service clicked:', serviceName);
            if (serviceName && window.snapseek) {
                await window.snapseek.openService(serviceName);
            }
        });
    });

    // Handle settings button
    if (settingsBtn) {
        settingsBtn.addEventListener('click', async () => {
            console.log('SnapSeek: Settings clicked');
            if (window.snapseek) {
                await window.snapseek.openSettings();
            }
        });
    }

    // Handle home button (previously back button)
    if (backBtn) {
        backBtn.addEventListener('click', async () => {
            console.log('SnapSeek: Home clicked');
            if (window.snapseek) {
                await window.snapseek.closeService();
            }
        });
    }

    // Handle browser back button
    if (browserBackBtn) {
        browserBackBtn.addEventListener('click', async () => {
            console.log('SnapSeek: Back clicked');
            if (window.snapseek) {
                await window.snapseek.goBack();
            }
        });
    }

    // Window Controls
    const minBtn = document.getElementById('min-btn');
    const maxBtn = document.getElementById('max-btn');
    const closeBtn = document.getElementById('close-btn');

    if (minBtn) minBtn.addEventListener('click', () => window.snapseek.minimize());
    if (maxBtn) maxBtn.addEventListener('click', () => window.snapseek.toggleMaximize());
    if (closeBtn) closeBtn.addEventListener('click', () => window.snapseek.close());

    // Listen for navigation bar show/hide events
    if (window.snapseek) {
        window.snapseek.onShowNavBar(() => {
            if (navControls) {
                navControls.classList.remove('hidden');
            }
            // Hide the service selector (cards) so they don't show through
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.add('hidden');
        });

        window.snapseek.onHideNavBar(() => {
            if (navControls) {
                navControls.classList.add('hidden');
            }
            // Show the service selector again
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.remove('hidden');
        });
    }
});

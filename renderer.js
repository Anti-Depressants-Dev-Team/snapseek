// Main renderer process for the service selector
document.addEventListener('DOMContentLoaded', () => {
    const serviceCards = document.querySelectorAll('.service-card');
    const settingsBtn = document.getElementById('settings-btn');
    const backBtn = document.getElementById('back-btn');
    const navBar = document.getElementById('nav-bar');

    // Handle service selection
    serviceCards.forEach(card => {
        card.addEventListener('click', async () => {
            const serviceName = card.dataset.service;
            if (serviceName && window.snapseek) {
                await window.snapseek.openService(serviceName);
            }
        });
    });

    // Handle settings button
    if (settingsBtn) {
        settingsBtn.addEventListener('click', async () => {
            if (window.snapseek) {
                await window.snapseek.openSettings();
            }
        });
    }

    // Handle back button
    if (backBtn) {
        backBtn.addEventListener('click', async () => {
            if (window.snapseek) {
                await window.snapseek.closeService();
            }
        });
    }

    // Listen for navigation bar show/hide events
    if (window.snapseek) {
        window.snapseek.onShowNavBar(() => {
            if (navBar) {
                navBar.classList.remove('hidden');
            }
            // Hide the service selector (cards) so they don't show through
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.add('hidden');
        });

        window.snapseek.onHideNavBar(() => {
            if (navBar) {
                navBar.classList.add('hidden');
            }
            // Show the service selector again
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.remove('hidden');
        });
    }
});

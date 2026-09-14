export function initSettings() {
    const currentPathDisplay = document.getElementById('current-path');
    const changePathBtn = document.getElementById('change-path-btn');
    const darkModeToggle = document.getElementById('dark-mode-toggle') as HTMLInputElement;

    async function loadCurrentPath() {
        if (window.snapseek && currentPathDisplay) {
            const path = await window.snapseek.getDownloadPath();
            currentPathDisplay.textContent = path;
        }
    }

    // Only load path when route opened
    window.addEventListener('route-settings', () => {
        loadCurrentPath();
    });

    if (changePathBtn) {
        changePathBtn.addEventListener('click', async () => {
            if (window.snapseek && currentPathDisplay) {
                const result = await window.snapseek.setDownloadPath();
                if (result.success && result.path) {
                    currentPathDisplay.textContent = result.path;
                }
            }
        });
    }

    // Dark Mode Toggle
    async function initDarkMode() {
        if (window.snapseek && darkModeToggle) {
            const isDarkMode = await window.snapseek.getDarkModeState();
            darkModeToggle.checked = isDarkMode;
        }
    }

    if (darkModeToggle) {
        darkModeToggle.addEventListener('change', async (e) => {
            const isChecked = (e.target as HTMLInputElement).checked;
            if (window.snapseek) {
                await window.snapseek.toggleDarkModeState(isChecked);
            }
        });
    }

    initDarkMode();
}

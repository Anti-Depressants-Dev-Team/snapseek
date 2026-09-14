export function initRouter() {
    const navLinks = document.querySelectorAll('.nav-link');

    // Set up click handlers for navigation links
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const target = (e.currentTarget as HTMLElement).dataset.target;
            if (target) {
                navigate(target);
            }
        });
    });

    // Listen to IPC navigation requests from the backend
    if (window.snapseek && window.snapseek.onNavigate) {
        window.snapseek.onNavigate((_event, target) => {
            navigate(target);
        });
    }
}

export function navigate(target: string) {
    const views = document.querySelectorAll('.view');
    views.forEach(v => {
        v.classList.remove('active');
        v.classList.add('hidden');
    });

    const targetView = document.getElementById(`view-${target}`);
    if (targetView) {
        targetView.classList.remove('hidden');
        // Let the browser paint next frame before adding active for transition
        requestAnimationFrame(() => {
            targetView.classList.add('active');
        });
    } else {
        console.error(`View ${target} not found`);
    }

    // specific initializations per route
    if (target === 'settings') {
        const event = new CustomEvent('route-settings');
        window.dispatchEvent(event);
    } else if (target === 'history') {
        const event = new CustomEvent('route-history');
        window.dispatchEvent(event);
    } else if (target === 'home') {
        const event = new CustomEvent('route-home');
        window.dispatchEvent(event);
    }
}

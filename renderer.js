// Main renderer process for the service selector
document.addEventListener('DOMContentLoaded', async () => {
    const serviceGrid = document.querySelector('.services-grid');
    const settingsBtn = document.getElementById('settings-btn');
    const backBtn = document.getElementById('back-btn');
    const navControls = document.getElementById('nav-controls');
    const browserBackBtn = document.getElementById('browser-back-btn');

    console.log('SnapSeek: Renderer loaded');

    // --- Dynamic Service Rendering ---

    async function renderServices() {
        if (!window.snapseek) return;

        const services = await window.snapseek.getAllServices();
        const states = await window.snapseek.getServiceStates();

        // Clear existing static cards (except if we want to keep a skeleton, but we replace all)
        serviceGrid.innerHTML = '';

        services.forEach(service => {
            const isEnabled = states[service.id] !== false; // Default to true if undefined

            const card = document.createElement('div');
            card.className = `service-card ${isEnabled ? '' : 'hidden'}`;
            card.dataset.service = service.id;

            // Icon handling
            let iconHtml = '';
            if (service.type === 'default') {
                // Use existing SVG logic or fallback
                iconHtml = getServiceIcon(service.id);
            } else {
                // Custom icon (image or placeholder)
                if (service.icon && service.icon.startsWith('http')) {
                    iconHtml = `<img src="${service.icon}" alt="${service.name}" class="custom-service-icon" style="width: 48px; height: 48px; border-radius: 8px; object-fit: cover;">`;
                } else {
                    // Fallback letter icon
                    iconHtml = `
                    <div style="width: 48px; height: 48px; border-radius: 8px; background: #333; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: bold; color: #fff;">
                        ${service.name.charAt(0).toUpperCase()}
                    </div>`;
                }
            }

            card.innerHTML = `
                <div class="service-icon ${service.id}">
                    ${iconHtml}
                </div>
                <h2 class="service-name">${service.name}</h2>
                <p class="service-description">${service.url}</p>
                 <div class="service-badge">Browse</div>
            `;

            // Add click listener
            card.addEventListener('click', async () => {
                console.log('SnapSeek: Service clicked:', service.id);
                await window.snapseek.openService(service.id);
            });

            serviceGrid.appendChild(card);
        });

        // Refresh the Manage Services Modal list as well
        renderManageServicesList(services, states);
    }

    function getServiceIcon(id) {
        // Return original SVGs for default services to maintain look
        const icons = {
            pinterest: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><path d="M24 4C12.96 4 4 12.96 4 24C4 32.52 9.48 39.72 17.04 42.36C16.92 40.92 16.8 38.64 17.16 37.08C17.52 35.64 19.32 27.84 19.32 27.84C19.32 27.84 18.72 26.64 18.72 24.84C18.72 21.96 20.4 19.8 22.56 19.8C24.36 19.8 25.2 21.12 25.2 22.68C25.2 24.48 24 27.24 23.4 29.76C22.92 31.92 24.48 33.72 26.64 33.72C30.48 33.72 33.48 29.52 33.48 23.64C33.48 18.48 29.76 14.88 24 14.88C17.4 14.88 13.56 19.68 13.56 24.48C13.56 26.28 14.28 28.2 15.12 29.28C15.24 29.52 15.24 29.64 15.24 29.88C15 31.08 14.52 32.64 14.4 33.12C14.28 33.72 14.04 33.84 13.44 33.6C10.68 32.28 8.88 28.68 8.88 24.36C8.88 17.28 14.04 10.8 24.6 10.8C33.12 10.8 39.72 16.8 39.72 23.52C39.72 30.6 34.92 36.36 28.44 36.36C26.28 36.36 24.24 35.16 23.52 33.84C23.52 33.84 22.44 38.16 22.2 39C21.72 40.92 20.4 43.32 19.56 44.88C21.24 45.36 22.56 45.6 24 45.6C35.04 45.6 44 36.6 44 24.6C44 12.96 35.04 4 24 4Z" fill="currentColor" /></svg>`,
            safebooru: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="8" y="8" width="32" height="32" rx="4" stroke="currentColor" stroke-width="3" /><circle cx="18" cy="18" r="3" fill="currentColor" /><path d="M40 28L32 20L20 32L12 24L8 28V36C8 37.1046 8.89543 38 10 38H38C39.1046 38 40 37.1046 40 36V28Z" fill="currentColor" /></svg>`,
            pixiv: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><path d="M24 4C12.96 4 4 12.96 4 24C4 35.04 12.96 44 24 44C35.04 44 44 35.04 44 24C44 12.96 35.04 4 24 4ZM28.8 26.4H20.4V32.4H16.8V15.6H28.8C31.68 15.6 34.08 18 34.08 20.88C34.08 23.76 31.68 26.4 28.8 26.4ZM20.4 19.2V22.8H28.8C29.76 22.8 30.48 22.08 30.48 21.12C30.48 20.16 29.76 19.2 28.8 19.2H20.4Z" fill="currentColor" /></svg>`,
            wallpapers: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="4" y="8" width="40" height="32" rx="4" stroke="currentColor" stroke-width="3" /><circle cx="14" cy="16" r="3" fill="currentColor" /><path d="M44 34L30 18L10 38" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /><path d="M30 32L22 24L14 32" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /></svg>`
        };
        return icons[id] || icons['wallpapers']; // Fallback
    }

    function renderManageServicesList(services, states) {
        const container = document.querySelector('.services-modal-content');
        // Keep header
        const header = container.querySelector('h3');
        container.innerHTML = '';
        container.appendChild(header);

        services.forEach(service => {
            const isEnabled = states[service.id] !== false;

            const item = document.createElement('div');
            item.className = 'service-toggle-item';

            // Toggle Switch
            const label = document.createElement('label');
            label.className = 'switch';

            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = isEnabled;
            input.dataset.service = service.id;

            input.addEventListener('change', async (e) => {
                await window.snapseek.toggleServiceState(service.id, e.target.checked);
                renderServices(); // Re-render main grid
            });

            const span = document.createElement('span');
            span.className = 'slider round';

            label.appendChild(input);
            label.appendChild(span);

            const nameSpan = document.createElement('span');
            nameSpan.textContent = service.name;

            item.appendChild(label);
            item.appendChild(nameSpan);

            // Delete button for custom services
            if (service.type === 'custom') {
                const deleteBtn = document.createElement('button');
                deleteBtn.textContent = '×';
                deleteBtn.style.marginLeft = 'auto'; // Push to right
                deleteBtn.style.background = 'transparent';
                deleteBtn.style.border = 'none';
                deleteBtn.style.color = '#ef4444';
                deleteBtn.style.fontSize = '20px';
                deleteBtn.style.cursor = 'pointer';
                deleteBtn.title = 'Remove Service';

                deleteBtn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    if (confirm(`Remove ${service.name}?`)) {
                        await window.snapseek.removeCustomService(service.id);
                        renderServices();
                    }
                });
                item.appendChild(deleteBtn);
            }

            container.appendChild(item);
        });

        // Add "Add Custom Service" Button
        const addBtn = document.createElement('button');
        addBtn.className = 'add-service-btn';
        addBtn.textContent = '+ Add Custom Service';
        addBtn.style.width = '100%';
        addBtn.style.padding = '10px';
        addBtn.style.marginTop = '15px';
        addBtn.style.background = '#8b5cf6';
        addBtn.style.color = 'white';
        addBtn.style.border = 'none';
        addBtn.style.borderRadius = '6px';
        addBtn.style.cursor = 'pointer';
        addBtn.style.fontWeight = '600';

        addBtn.onclick = () => {
            // Show add modal
            document.getElementById('add-service-modal').classList.remove('hidden');
            document.getElementById('services-modal').classList.add('hidden'); // Close manage modal
        };

        container.appendChild(addBtn);
    }

    // --- Initial Render ---
    await renderServices();

    // Handle settings button
    if (settingsBtn) {
        settingsBtn.addEventListener('click', async () => {
            console.log('SnapSeek: Settings clicked');
            if (window.snapseek) {
                await window.snapseek.openSettings();
            }
        });
    }

    // Handle home button
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
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.add('hidden');
        });

        window.snapseek.onHideNavBar(() => {
            if (navControls) {
                navControls.classList.add('hidden');
            }
            const selector = document.getElementById('service-selector');
            if (selector) selector.classList.remove('hidden');
        });
    }

    // Services UI Toggling
    const servicesToggleBtn = document.getElementById('services-toggle-btn');
    const servicesModal = document.getElementById('services-modal');

    if (servicesToggleBtn && servicesModal) {
        servicesToggleBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            servicesModal.classList.toggle('hidden');
        });

        document.addEventListener('click', (e) => {
            // Handle both modals closing on outside click
            const addModal = document.getElementById('add-service-modal');

            if (!servicesToggleBtn.contains(e.target) && !servicesModal.contains(e.target)) {
                servicesModal.classList.add('hidden');
            }
            // Logic handled in add modal separate event listeners usually, but global here works too if precise
        });
    }

    // Setup Add Service Modal Logic
    setupAddServiceModal();

    function setupAddServiceModal() {
        const modal = document.getElementById('add-service-modal');
        const form = document.getElementById('add-service-form');
        const cancelBtn = document.getElementById('add-service-cancel');

        if (!modal || !form) return;

        cancelBtn.addEventListener('click', () => {
            modal.classList.add('hidden');
            document.getElementById('services-modal').classList.remove('hidden'); // Re-open manage
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = document.getElementById('new-service-name').value;
            const url = document.getElementById('new-service-url').value;
            const iconUrl = document.getElementById('new-service-icon').value;

            if (name && url) {
                await window.snapseek.addCustomService({ name, url, iconUrl });
                modal.classList.add('hidden');
                form.reset();
                renderServices();
            }
        });
    }

});

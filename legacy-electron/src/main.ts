import './types';
import { initRouter } from './router';
import { initSettings } from './settings';
import { initHistory } from './history';
import { Service } from './types';

document.addEventListener('DOMContentLoaded', async () => {
    console.log('SnapSeek SPA: Vanilla TypeScript UI loaded');

    // Initialize Modules
    initRouter();
    initSettings();
    initHistory();

    const serviceGrid = document.getElementById('services-grid');
    const navControls = document.getElementById('nav-controls');
    const browserBackBtn = document.getElementById('browser-back-btn');
    const backBtn = document.getElementById('back-btn'); // Home (Close Service) button
    const openFolderBtn = document.getElementById('open-folder-btn');

    // Make global for inline HTML calls
    window.openService = async (serviceId: string) => {
        if (window.snapseek) {
            await window.snapseek.openService(serviceId);
        }
    };

    window.updateServiceRegion = async (serviceId: string, newUrl: string) => {
        if (window.snapseek) {
            await window.snapseek.updateServiceUrl(serviceId, newUrl);
        }
    };

    // Initial render
    await renderServices();

    // ----------------------------------
    // Event Listeners
    // ----------------------------------

    if (backBtn) {
        backBtn.addEventListener('click', async () => {
            if (window.snapseek) {
                await window.snapseek.closeService();
            }
        });
    }

    if (browserBackBtn) {
        browserBackBtn.addEventListener('click', async () => {
            if (window.snapseek) {
                await window.snapseek.goBack();
            }
        });
    }

    if (openFolderBtn) {
        openFolderBtn.addEventListener('click', async () => {
            if (window.snapseek) {
                await window.snapseek.openDownloadsFolder();
            }
        });
    }

    // Window Controls
    const minBtn = document.getElementById('min-btn');
    const maxBtn = document.getElementById('max-btn');
    const closeBtn = document.getElementById('close-btn');

    if (minBtn) minBtn.addEventListener('click', () => window.snapseek?.minimize());
    if (maxBtn) maxBtn.addEventListener('click', () => window.snapseek?.toggleMaximize());
    if (closeBtn) closeBtn.addEventListener('click', () => window.snapseek?.close());

    // Navigation Bar states
    if (window.snapseek) {
        window.snapseek.onShowNavBar(() => {
            if (navControls) navControls.classList.remove('hidden');
            const spaContainer = document.getElementById('spa-container');
            if (spaContainer) spaContainer.classList.add('hidden');
        });

        window.snapseek.onHideNavBar(() => {
            if (navControls) navControls.classList.add('hidden');
            const spaContainer = document.getElementById('spa-container');
            if (spaContainer) spaContainer.classList.remove('hidden');
        });
    }

    setupModals();

    // ----------------------------------
    // Rendering Logic
    // ----------------------------------

    async function renderServices() {
        if (!serviceGrid) return;

        let services: Service[] = [];
        let states: Record<string, boolean> = {};

        if (window.snapseek) {
            services = await window.snapseek.getAllServices();
            states = await window.snapseek.getServiceStates();
        } else {
            // Mock data for browser testing
            services = [
                { id: 'pinterest', name: 'Pinterest', url: 'https://ru.pinterest.com/', icon: 'pinterest', type: 'default' },
                { id: 'safebooru', name: 'Safebooru', url: 'https://safebooru.org/', icon: 'safebooru', type: 'default' },
                { id: 'pixiv', name: 'Pixiv', url: 'https://www.pixiv.net/', icon: 'pixiv', type: 'default' }
            ];
            states = { pinterest: true, safebooru: true, pixiv: true };
        }

        serviceGrid.innerHTML = '';

        services.forEach(service => {
            const isEnabled = states[service.id] !== false;

            const card = document.createElement('div');
            card.className = `service-card ${isEnabled ? '' : 'hidden'}`;
            card.dataset.service = service.id;

            let urlElement = `<p class="service-description">${service.url}</p>`;
            if (service.regions && service.regions.length > 0) {
                const options = service.regions.map(r =>
                    `<option value="${r.url}" ${service.url === r.url ? 'selected' : ''}>${r.name}</option>`
                ).join('');

                urlElement = `
                    <div class="service-description" style="margin-bottom: 20px;">
                        <select class="region-select region-select-input" data-service-id="${service.id}">
                            ${options}
                        </select>
                    </div>
                `;
            }

            let iconHtml = getServiceIcon(service.id);
            if (service.type === 'custom') {
                if (service.icon && service.icon.startsWith('http')) {
                    iconHtml = `<img src="${service.icon}" alt="${service.name}" style="width: 48px; height: 48px; border-radius: 8px; object-fit: cover;">`;
                } else {
                    iconHtml = `
                    <div style="width: 48px; height: 48px; border-radius: 12px; background: rgba(255,255,255,0.1); display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: bold; color: #fff; box-shadow: inset 0 0 10px rgba(255,255,255,0.05);">
                        ${service.name.charAt(0).toUpperCase()}
                    </div>`;
                }
            }

            card.innerHTML = `
                <div class="service-icon ${service.id}">
                    ${iconHtml}
                </div>
                <h2 class="service-name">${service.name}</h2>
                ${urlElement}
                <button class="primary-button browse-collection-btn" data-service-id="${service.id}">Browse Collection</button>
            `;

            serviceGrid.appendChild(card);
        });

        // Event delegation for Browse Collection buttons
        const browseBtns = document.querySelectorAll('.browse-collection-btn');
        browseBtns.forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const target = e.currentTarget as HTMLElement;
                const id = target.dataset.serviceId;
                if (id && window.snapseek) {
                    await window.snapseek.openService(id);
                }
            });
        });

        // Event delegation for region dropdowns
        const regionSelects = document.querySelectorAll('.region-select-input');
        regionSelects.forEach(select => {
            select.addEventListener('change', async (e) => {
                const target = e.currentTarget as HTMLSelectElement;
                const id = target.dataset.serviceId;
                if (id && window.snapseek) {
                    await window.snapseek.updateServiceUrl(id, target.value);
                }
            });
            select.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        });

        renderManageServicesList(services, states);
    }

    function renderManageServicesList(services: Service[], states: Record<string, boolean>) {
        const container = document.getElementById('services-modal-content');
        if (!container) return;

        const header = container.querySelector('h3');
        container.innerHTML = '';
        if (header) container.appendChild(header);

        services.forEach(service => {
            const isEnabled = states[service.id] !== false;

            const item = document.createElement('div');
            item.className = 'service-toggle-item';

            const label = document.createElement('label');
            label.className = 'switch';

            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = isEnabled;
            input.dataset.service = service.id;

            input.addEventListener('change', async (e) => {
                const target = e.target as HTMLInputElement;
                await window.snapseek.toggleServiceState(service.id, target.checked);
                renderServices();
            });

            const span = document.createElement('span');
            span.className = 'slider round';

            label.appendChild(input);
            label.appendChild(span);

            const nameSpan = document.createElement('span');
            nameSpan.textContent = service.name;

            item.appendChild(label);
            item.appendChild(nameSpan);

            if (service.type === 'custom') {
                const deleteBtn = document.createElement('button');
                deleteBtn.textContent = '×';
                deleteBtn.style.cssText = 'margin-left: auto; background: transparent; border: none; color: #ef4444; font-size: 20px; cursor: pointer; padding: 0 5px;';
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

        const addBtn = document.createElement('button');
        addBtn.textContent = '+ Add Custom Service';
        addBtn.className = 'primary-button primary-glow';
        addBtn.style.marginTop = '15px';

        addBtn.onclick = () => {
            document.getElementById('add-service-modal')?.classList.remove('hidden');
            document.getElementById('services-modal')?.classList.add('hidden');
        };

        container.appendChild(addBtn);
    }

    function setupModals() {
        const servicesToggleBtn = document.getElementById('services-toggle-btn');
        const servicesModal = document.getElementById('services-modal');
        const addModal = document.getElementById('add-service-modal');
        const form = document.getElementById('add-service-form');
        const cancelBtn = document.getElementById('add-service-cancel');

        if (servicesToggleBtn && servicesModal) {
            servicesToggleBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                servicesModal.classList.toggle('hidden');
                addModal?.classList.add('hidden');
            });

            document.addEventListener('click', (e) => {
                const target = e.target as Element;
                if (!servicesToggleBtn.contains(target) && !servicesModal.contains(target)) {
                    servicesModal.classList.add('hidden');
                }
                if (addModal && !addModal.contains(target) && !servicesModal.contains(target) && !servicesToggleBtn.contains(target)) {
                    addModal.classList.add('hidden');
                }
            });
        }

        if (addModal && form && cancelBtn) {
            cancelBtn.addEventListener('click', () => {
                addModal.classList.add('hidden');
                servicesModal?.classList.remove('hidden');
            });

            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const name = (document.getElementById('new-service-name') as HTMLInputElement).value;
                const url = (document.getElementById('new-service-url') as HTMLInputElement).value;
                const iconUrl = (document.getElementById('new-service-icon') as HTMLInputElement).value;

                if (name && url) {
                    await window.snapseek.addCustomService({ name, url, iconUrl });
                    addModal.classList.add('hidden');
                    (form as HTMLFormElement).reset();
                    renderServices();
                }
            });
        }
    }

    function getServiceIcon(id: string) {
        const icons: Record<string, string> = {
            pinterest: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><path d="M24 4C12.96 4 4 12.96 4 24C4 32.52 9.48 39.72 17.04 42.36C16.92 40.92 16.8 38.64 17.16 37.08C17.52 35.64 19.32 27.84 19.32 27.84C19.32 27.84 18.72 26.64 18.72 24.84C18.72 21.96 20.4 19.8 22.56 19.8C24.36 19.8 25.2 21.12 25.2 22.68C25.2 24.48 24 27.24 23.4 29.76C22.92 31.92 24.48 33.72 26.64 33.72C30.48 33.72 33.48 29.52 33.48 23.64C33.48 18.48 29.76 14.88 24 14.88C17.4 14.88 13.56 19.68 13.56 24.48C13.56 26.28 14.28 28.2 15.12 29.28C15.24 29.52 15.24 29.64 15.24 29.88C15 31.08 14.52 32.64 14.4 33.12C14.28 33.72 14.04 33.84 13.44 33.6C10.68 32.28 8.88 28.68 8.88 24.36C8.88 17.28 14.04 10.8 24.6 10.8C33.12 10.8 39.72 16.8 39.72 23.52C39.72 30.6 34.92 36.36 28.44 36.36C26.28 36.36 24.24 35.16 23.52 33.84C23.52 33.84 22.44 38.16 22.2 39C21.72 40.92 20.4 43.32 19.56 44.88C21.24 45.36 22.56 45.6 24 45.6C35.04 45.6 44 36.6 44 24.6C44 12.96 35.04 4 24 4Z" fill="currentColor" /></svg>`,
            safebooru: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="8" y="8" width="32" height="32" rx="4" stroke="currentColor" stroke-width="3" /><circle cx="18" cy="18" r="3" fill="currentColor" /><path d="M40 28L32 20L20 32L12 24L8 28V36C8 37.1046 8.89543 38 10 38H38C39.1046 38 40 37.1046 40 36V28Z" fill="currentColor" /></svg>`,
            pixiv: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><path d="M24 4C12.96 4 4 12.96 4 24C4 35.04 12.96 44 24 44C35.04 44 44 35.04 44 24C44 12.96 35.04 4 24 4ZM28.8 26.4H20.4V32.4H16.8V15.6H28.8C31.68 15.6 34.08 18 34.08 20.88C34.08 23.76 31.68 26.4 28.8 26.4ZM20.4 19.2V22.8H28.8C29.76 22.8 30.48 22.08 30.48 21.12C30.48 20.16 29.76 19.2 28.8 19.2H20.4Z" fill="currentColor" /></svg>`,
            deviantart: `<svg width="48" height="48" viewBox="0 0 24 24" fill="none"><path d="M19.207 4.794l.23-.43V0H15.07l-.436.44-2.058 3.925-.646.436H4.58v5.993h4.04l.36.436-4.175 7.98-.24.43V24H8.93l.436-.44 2.07-3.925.644-.436h7.35v-5.993h-4.05l-.36-.438 4.186-7.977z" fill="currentColor" /></svg>`,
            giphy: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="4" y="4" width="40" height="40" rx="4" fill="black"/><path d="M10 10h28v6h-22v16h22v-10h-8v-6h14v22h-34z" fill="url(#g)"/><defs><linearGradient id="g" x1="4" y1="4" x2="44" y2="44" gradientUnits="userSpaceOnUse"><stop stop-color="#0cf"/><stop offset="1" stop-color="#93f"/></linearGradient></defs></svg>`,
            tenor: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="4" y="4" width="40" height="40" rx="4" fill="#2d93dd"/><path d="M14 14h20v6h-7v14h-6v-14h-7z" fill="white"/></svg>`,
            wallpapers: `<svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect x="4" y="8" width="40" height="32" rx="4" stroke="currentColor" stroke-width="3" /><circle cx="14" cy="16" r="3" fill="currentColor" /><path d="M44 34L30 18L10 38" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /><path d="M30 32L22 24L14 32" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /></svg>`
        };
        return icons[id] || icons['wallpapers'];
    }
});

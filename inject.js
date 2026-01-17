// This script is injected into browsed pages to add download buttons to images
(function () {
    'use strict';

    // Avoid running multiple times
    if (window._snapseekInjected) return;
    window._snapseekInjected = true;

    // Global Styles for Download Overlay
    const style = document.createElement('style');
    style.textContent = `
        .snapseek-overlay {
            position: absolute;
            z-index: 999999;
            display: flex;
            gap: 5px;
            padding: 5px;
            background: rgba(0, 0, 0, 0.8);
            border-radius: 8px;
            backdrop-filter: blur(5px);
            opacity: 0;
            transition: opacity 0.2s ease;
            pointer-events: none; /* Initially simplify interactions */
        }
        .snapseek-overlay.visible {
            opacity: 1;
            pointer-events: auto;
        }
        .snapseek-btn {
            background: rgba(255, 255, 255, 0.1);
            color: white;
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 4px;
            padding: 4px 8px;
            font-family: system-ui, -apple-system, sans-serif;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            text-transform: uppercase;
        }
        .snapseek-btn:hover {
            background: rgba(255, 255, 255, 0.25);
            transform: scale(1.05);
        }
        .snapseek-btn.primary {
            background: #8b5cf6; /* Purple accent */
            border-color: #8b5cf6;
        }
        .snapseek-btn.primary:hover {
            background: #7c3aed;
        }
    `;
    document.head.appendChild(style);

    // Global force-visibility style (existing)
    const visibilityStyle = document.createElement('style');
    visibilityStyle.textContent = `
        img {
            opacity: 1!important;
            visibility: visible!important;
            transition: none!important;
            filter: none!important;
        }
    `;
    document.head.appendChild(visibilityStyle);

    // Overlay Element
    let overlay = document.createElement('div');
    overlay.className = 'snapseek-overlay';
    overlay.innerHTML = `
        <button class="snapseek-btn primary" data-fmt="png">PNG</button>
        <button class="snapseek-btn" data-fmt="jpg">JPG</button>
        <button class="snapseek-btn" data-fmt="gif">GIF</button>
    `;
    document.body.appendChild(overlay);

    let currentTargetImg = null;
    let hideTimeout = null;

    // Handle button clicks
    overlay.addEventListener('click', (e) => {
        const btn = e.target.closest('.snapseek-btn');
        if (btn && currentTargetImg) {
            e.stopPropagation();
            e.preventDefault();

            const format = btn.dataset.fmt;
            // Get highest resolution source
            const src = currentTargetImg.dataset.forcedSrc || currentTargetImg.src;

            if (src) {
                console.log('SnapSeek: Downloading', src, 'as', format);
                // Visual feedback
                const originalText = btn.textContent;
                btn.textContent = '...';

                if (window.snapseek) {
                    window.snapseek.downloadImage(src, format);
                    setTimeout(() => {
                        btn.textContent = '✓';
                        setTimeout(() => btn.textContent = originalText, 1000);
                    }, 500);
                }
            }
        }
    });

    // Handle Mouse Over/Out for Images
    document.addEventListener('mouseover', (e) => {
        if (e.target.tagName === 'IMG') {
            const img = e.target;

            // Check if image is substantial enough to download
            if (img.width < 50 || img.height < 50) return;

            clearTimeout(hideTimeout);
            currentTargetImg = img;

            // Position overlay
            const rect = img.getBoundingClientRect();
            // detailed positioning: bottom right of the image, but within viewport
            let top = rect.bottom + window.scrollY - 40;
            let left = rect.right + window.scrollX - 160;

            // Adjust if out of bounds (fallback to basic positioning logic)
            if (left < 0) left = rect.left + window.scrollX;

            overlay.style.top = `${top}px`;
            overlay.style.left = `${left}px`;

            // If image is fixed/sticky, we might need a different strategy, 
            // but for now absolute positioning relative to document works for static streams.
            // For scrolling feeds, re-calculating on loop or using 'fixed' might be better but 'absolute' maps well to document flow.

            overlay.classList.add('visible');
        } else if (overlay.contains(e.target)) {
            clearTimeout(hideTimeout);
        }
    }, true);

    document.addEventListener('mouseout', (e) => {
        if (e.target.tagName === 'IMG' || overlay.contains(e.target)) {
            hideTimeout = setTimeout(() => {
                // Check if we moved to the overlay or the image
                if (!overlay.matches(':hover') && (!currentTargetImg || !currentTargetImg.matches(':hover'))) {
                    overlay.classList.remove('visible');
                }
            }, 100);
        }
    }, true);


    // Track observed images to avoid double-binding (Existing Hydration Logic)
    const observedImages = new WeakSet();

    function processImage(img) {
        // 1. Basic attribute stripping
        if (img.getAttribute('loading') === 'lazy') {
            img.removeAttribute('loading');
            img.setAttribute('loading', 'eager');
            img.setAttribute('decoding', 'sync');
        }

        // 2. Resolve best source
        let newSrc = img.getAttribute('data-src') || img.getAttribute('data-lazy-src');

        if (img.srcset) {
            const parts = img.srcset.split(',');
            if (parts.length > 0) {
                const bestCandidate = parts[parts.length - 1].trim().split(' ')[0];
                if (bestCandidate) {
                    newSrc = bestCandidate;
                }
            }
        }

        // 3. Apply best source if needed
        if (newSrc && img.src !== newSrc) {
            img.src = newSrc;
            img.dataset.forcedSrc = newSrc;
            img.removeAttribute('srcset');
        } else if (!img.dataset.forcedSrc && img.src) {
            img.dataset.forcedSrc = img.src;
        }

        // 4. Mutation Observer logic
        if (!observedImages.has(img)) {
            observedImages.add(img);
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    if (mutation.type === 'attributes') {
                        const target = mutation.target;
                        if (mutation.attributeName === 'style' || mutation.attributeName === 'class') {
                            target.style.opacity = '1';
                            target.style.visibility = 'visible';
                        }
                        if ((mutation.attributeName === 'src' || mutation.attributeName === 'srcset') && target.dataset.forcedSrc) {
                            if (target.src !== target.dataset.forcedSrc) {
                                target.src = target.dataset.forcedSrc;
                                target.removeAttribute('srcset');
                            }
                        }
                    }
                });
            });
            observer.observe(img, {
                attributes: true,
                attributeFilter: ['src', 'srcset', 'style', 'class', 'loading']
            });
        }

        // Force visibility
        img.style.opacity = '1';
        img.style.visibility = 'visible';
    }

    // Force all current images
    function forceImageLoad() {
        const images = document.querySelectorAll('img');
        images.forEach(processImage);
    }

    setInterval(forceImageLoad, 500);
    forceImageLoad();

    // Watch for dynamically added images
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            mutation.addedNodes.forEach((node) => {
                if (node.nodeName === 'IMG') {
                    processImage(node);
                } else if (node.querySelectorAll) {
                    const images = node.querySelectorAll('img');
                    images.forEach(img => {
                        processImage(img);
                    });
                }
            });
        });
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    console.log('SnapSeek: Inject loaded with Download Overlay & Hydration');
})();

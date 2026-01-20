// This script is injected into browsed pages to add download buttons to images
(function () {
    'use strict';

    // Avoid running multiple times
    if (window._snapseekInjected) return;
    window._snapseekInjected = true;

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

    // Overlay removed as per user request



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

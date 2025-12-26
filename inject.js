// This script is injected into browsed pages to add download buttons to images
(function () {
    'use strict';

    // Avoid running multiple times
    if (window._snapseekInjected) return;
    window._snapseekInjected = true;

    const BUTTON_CLASS = 'snapseek-dl-btn';

    // Styles for the download button overlay
    const style = document.createElement('style');
    style.textContent = `
    .snapseek-dl-btn {
      position: absolute;
      bottom: 8px;
      right: 8px;
      background: rgba(0, 0, 0, 0.85);
      color: white;
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 50%;
      width: 40px;
      height: 40px;
      display: none;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      z-index: 999999;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.6);
      transition: all 0.2s ease;
      backdrop-filter: blur(8px);
      padding: 0;
    }

    .snapseek-dl-btn svg {
      width: 20px;
      height: 20px;
      fill: white;
    }

    .snapseek-dl-btn:hover {
      background: rgba(0, 0, 0, 0.95);
      transform: scale(1.15);
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.8);
    }

    .snapseek-dl-btn:active {
      transform: scale(1.05);
    }

    .snapseek-dl-btn.downloading {
      background: rgba(75, 85, 99, 0.9);
      cursor: wait;
    }

    .snapseek-dl-btn.success {
      background: rgba(16, 185, 129, 0.95);
    }

    .snapseek-dl-btn.error {
      background: rgba(239, 68, 68, 0.95);
    }

    /* Container for positioning */
    .snapseek-img-container {
      position: relative;
      display: inline-block;
    }

    .snapseek-img-container .snapseek-dl-btn {
      display: flex !important;
      opacity: 0.6;
    }

    .snapseek-img-container:hover .snapseek-dl-btn {
      opacity: 1;
    }
  `;
    document.head.appendChild(style);

    // SVG Icons
    const icons = {
        download: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12 16L7 11L8.4 9.6L11 12.2V4H13V12.2L15.6 9.6L17 11L12 16ZM6 20C5.45 20 4.979 19.804 4.587 19.412C4.195 19.02 3.99934 18.5493 4 18V15H6V18H18V15H20V18C20 18.55 19.804 19.021 19.412 19.413C19.02 19.805 18.5493 20.0007 18 20H6Z"/></svg>',
        loading: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"><animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="1s" repeatCount="indefinite"/></path></svg>',
        check: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"/></svg>',
        error: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12 19 6.41z"/></svg>'
    };

    function getImageFilename(img) {
        let filename = '';

        // Try alt attribute
        if (img.alt) {
            filename = img.alt;
        }
        // Try data attributes
        else if (img.dataset && img.dataset.filename) {
            filename = img.dataset.filename;
        }
        // Try src filename
        else if (img.src) {
            const urlParts = img.src.split('/');
            filename = urlParts[urlParts.length - 1].split('?')[0];
        }

        return filename || `snapseek_${Date.now()}`;
    }

    function createDownloadButton(img) {
        const button = document.createElement('button');
        button.className = BUTTON_CLASS;
        button.innerHTML = icons.download;
        button.title = 'Download as PNG';

        button.addEventListener('click', async (e) => {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();

            // Get the actual image URL
            let imageUrl = img.currentSrc || img.src;

            // Try to get highest quality version from srcset
            if (img.srcset) {
                const srcsetParts = img.srcset.split(',');
                if (srcsetParts.length > 0) {
                    const lastSrc = srcsetParts[srcsetParts.length - 1].trim().split(' ')[0];
                    imageUrl = lastSrc;
                }
            }

            if (!imageUrl) {
                button.innerHTML = icons.error;
                button.classList.add('error');
                setTimeout(() => {
                    button.innerHTML = icons.download;
                    button.classList.remove('error');
                }, 2000);
                return;
            }

            // Show downloading state
            button.innerHTML = icons.loading;
            button.classList.add('downloading');
            button.disabled = true;

            try {
                const filename = getImageFilename(img);

                // Check if snapseek API is available
                if (!window.snapseek || !window.snapseek.downloadImage) {
                    throw new Error('SnapSeek API not available');
                }

                const result = await window.snapseek.downloadImage(imageUrl, filename);

                if (result.success) {
                    button.innerHTML = icons.check;
                    button.classList.remove('downloading');
                    button.classList.add('success');

                    setTimeout(() => {
                        button.innerHTML = icons.download;
                        button.classList.remove('success');
                        button.disabled = false;
                    }, 2000);
                } else {
                    throw new Error(result.error || 'Download failed');
                }
            } catch (error) {
                console.error('SnapSeek download error:', error);
                button.innerHTML = icons.error;
                button.classList.remove('downloading');
                button.classList.add('error');

                setTimeout(() => {
                    button.innerHTML = icons.download;
                    button.classList.remove('error');
                    button.disabled = false;
                }, 2000);
            }
        });

        return button;
    }

    function addButtonToImage(img) {
        // Skip if already processed
        if (img.dataset.snapseekProcessed) return;

        // Skip very small images
        if (img.naturalWidth < 100 || img.naturalHeight < 100) return;

        // Skip if no valid src
        if (!img.src && !img.currentSrc) return;

        // Mark as processed
        img.dataset.snapseekProcessed = 'true';

        // Get parent container
        const parent = img.parentElement;
        if (!parent) return;

        // Check if parent can be positioned relatively
        const parentStyle = window.getComputedStyle(parent);
        const parentPosition = parentStyle.position;

        // Create container if needed
        let container;
        if (parentPosition === 'static' || parentPosition === '') {
            // Parent is static, we need to wrap
            container = document.createElement('div');
            container.className = 'snapseek-img-container';

            // Copy relevant image styles to container
            const imgStyle = window.getComputedStyle(img);
            if (imgStyle.display === 'block') {
                container.style.display = 'block';
            }
            if (imgStyle.width && imgStyle.width !== 'auto') {
                container.style.width = imgStyle.width;
            }

            parent.insertBefore(container, img);
            container.appendChild(img);
        } else {
            // Parent already positioned, use it directly
            container = parent;
            if (!container.classList.contains('snapseek-img-container')) {
                container.classList.add('snapseek-img-container');
            }
        }

        // Create and add button
        const button = createDownloadButton(img);
        container.appendChild(button);
    }

    function processAllImages() {
        const images = document.querySelectorAll('img');
        images.forEach(img => {
            if (img.complete && img.naturalWidth > 0) {
                addButtonToImage(img);
            } else {
                img.addEventListener('load', () => addButtonToImage(img), { once: true });
            }
        });
    }

    // Initial processing
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', processAllImages);
    } else {
        // Use setTimeout to ensure page is fully rendered
        setTimeout(processAllImages, 500);
    }

    // Watch for dynamically added images
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            mutation.addedNodes.forEach((node) => {
                if (node.nodeName === 'IMG') {
                    if (node.complete && node.naturalWidth > 0) {
                        addButtonToImage(node);
                    } else {
                        node.addEventListener('load', () => addButtonToImage(node), { once: true });
                    }
                } else if (node.querySelectorAll) {
                    const images = node.querySelectorAll('img');
                    images.forEach(img => {
                        if (img.complete && img.naturalWidth > 0) {
                            addButtonToImage(img);
                        } else {
                            img.addEventListener('load', () => addButtonToImage(img), { once: true });
                        }
                    });
                }
            });
        });
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    console.log('SnapSeek: Image download overlay active');
})();

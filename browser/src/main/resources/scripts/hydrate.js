// SnapSeek: make lazy images load and stay visible. Observers only, no polling.
(function () {
  if (window.__snapseekHydrated) return;
  window.__snapseekHydrated = true;

  var style = document.createElement('style');
  style.textContent = 'img{opacity:1!important;visibility:visible!important;filter:none!important}';
  (document.head || document.documentElement).appendChild(style);

  function fix(img) {
    if (img.getAttribute('loading') === 'lazy') img.setAttribute('loading', 'eager');
    if (!img.getAttribute('src')) {
      var lazy = img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || img.getAttribute('data-original');
      if (lazy) img.src = lazy;
    }
  }

  document.querySelectorAll('img').forEach(fix);

  new MutationObserver(function (mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      if (m.type === 'attributes') {
        if (m.target.tagName === 'IMG') fix(m.target);
        continue;
      }
      m.addedNodes.forEach(function (n) {
        if (n.nodeType !== 1) return;
        if (n.tagName === 'IMG') fix(n);
        else if (n.querySelectorAll) n.querySelectorAll('img').forEach(fix);
      });
    }
  }).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['loading', 'data-src'],
  });
})();

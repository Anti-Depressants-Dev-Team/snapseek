// SnapSeek page bridge: Alt+click saves an image; right-click tells the app the best URL for the image under the cursor.
(function () {
  if (window.__snapseekBridge) return;
  window.__snapseekBridge = true;

  function send(payload) {
    if (typeof window.snapseekQuery !== 'function') return;
    window.snapseekQuery({ request: JSON.stringify(payload), onSuccess: function () {}, onFailure: function () {} });
  }

  // Largest candidate from srcset, else the current source.
  function best(img) {
    var url = img.currentSrc || img.src;
    var set = img.getAttribute('srcset');
    if (set) {
      var bestW = -1;
      set.split(',').forEach(function (c) {
        var parts = c.trim().split(/\s+/);
        var w = parts[1] ? parseFloat(parts[1]) : 0;
        if (parts[0] && w > bestW) { bestW = w; url = parts[0]; }
      });
    }
    try { return new URL(url, location.href).href; } catch (e) { return url; }
  }

  // The <img> under the pointer, even when a transparent overlay sits on top of it (Pinterest, DeviantArt).
  function imageAt(e) {
    var stack = document.elementsFromPoint(e.clientX, e.clientY);
    for (var i = 0; i < stack.length; i++) {
      if (stack[i].tagName === 'IMG') return stack[i];
    }
    var el = e.target && e.target.closest ? e.target.closest('img, picture') : null;
    if (el && el.tagName === 'PICTURE') el = el.querySelector('img');
    return el;
  }

  addEventListener('contextmenu', function (e) {
    var img = imageAt(e);
    if (img) send({ type: 'hint', src: img.currentSrc || img.src, best: best(img), page: location.href });
  }, true);

  addEventListener('click', function (e) {
    if (!e.altKey) return;
    var img = imageAt(e);
    if (!img) return;
    e.preventDefault();
    e.stopPropagation();
    send({ type: 'save', src: best(img), page: location.href });
  }, true);
})();

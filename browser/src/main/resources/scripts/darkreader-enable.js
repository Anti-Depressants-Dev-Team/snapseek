// SnapSeek: turn on Dark Reader (bundled above) with the same look the Electron app used.
(function () {
  if (!window.DarkReader || window.__snapseekDark) return;
  window.__snapseekDark = true;
  try { DarkReader.setFetchMethod(window.fetch); } catch (e) {}
  DarkReader.enable({
    brightness: 100,
    contrast: 100,
    sepia: 0,
    grayscale: 0,
    mode: 1,
    darkSchemeBackgroundColor: '#181a1b',
    darkSchemeTextColor: '#e8e6e3',
  });
})();

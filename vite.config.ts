import { defineConfig } from 'vite';

export default defineConfig({
  base: './', // Use relative paths to make compiled files work in Electron filesystem
  root: 'src',
  build: {
    outDir: '../dist-ui',
    emptyOutDir: true,
  }
});

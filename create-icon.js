const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const toIco = require('to-ico');

async function createIcon() {
  const logoPath = path.join(__dirname, 'logo.png');
  const iconPath = path.join(__dirname, 'icon.ico');
  
  if (!fs.existsSync(logoPath)) {
    console.error('logo.png not found!');
    process.exit(1);
  }
  
  console.log('Creating icon.ico from logo.png...');
  
  // Windows icons typically need these sizes
  const sizes = [16, 32, 48, 64, 128, 256];
  const pngBuffers = [];
  
  // Generate PNG buffers for each size
  for (const size of sizes) {
    const buffer = await sharp(logoPath)
      .resize(size, size, {
        fit: 'contain',
        background: { r: 0, g: 0, b: 0, alpha: 0 }
      })
      .png()
      .toBuffer();
    
    pngBuffers.push(buffer);
    console.log(`Generated ${size}x${size} icon`);
  }
  
  // Convert PNG buffers to ICO
  const icoBuffer = await toIco(pngBuffers);
  
  // Write ICO file
  fs.writeFileSync(iconPath, icoBuffer);
  
  console.log(`✓ Successfully created icon.ico (${(icoBuffer.length / 1024).toFixed(2)} KB)`);
}

createIcon().catch(error => {
  console.error('Error creating icon:', error);
  process.exit(1);
});


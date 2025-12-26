# SnapSeek 🔍

Modern Electron application for browsing and downloading images from Pinterest, Safebooru, and Pixiv with integrated ad-blocking and automatic PNG conversion.

![Version](https://img.shields.io/badge/version-1.0.0-purple)
![License](https://img.shields.io/badge/license-MIT-blue)

## ✨ Features

- **🌐 In-App Browsing**: Browse Pinterest, Safebooru, and Pixiv without leaving the app
- **🚫 Ad Blocking**: Built-in ad blocker using EasyList for a cleaner browsing experience
- **🌙 Automatic Dark Mode**: Dark Reader integration for comfortable browsing on all websites
- **⬇️ Smart Downloads**: Overlay download buttons on all images with hover detection
- **🖼️ Automatic PNG Conversion**: All downloaded images are converted to PNG format using Sharp
- **⚙️ Persistent Settings**: Your download directory preference is saved across sessions
- **🎨 Modern UI**: Beautiful purple and black theme with smooth animations
- **⚡ Fast & Responsive**: Optimized for performance and stability

## 🚀 Getting Started

### Prerequisites

- Node.js (v16 or higher)
- npm or yarn

### Installation

1. Clone the repository:
```bash
git clone https://github.com/Yabosen/snapseek.git
cd snapseek
```

2. Install dependencies:
```bash
npm install
```

3. Start the application:
```bash
npm start
```

## 📖 Usage

### Browsing Images

1. Launch the app and select a service (Pinterest, Safebooru, or Pixiv)
2. Browse the website normally within the app
3. Hover over any image to reveal the download button
4. Click the download button to save the image as PNG

### Changing Settings

1. Click the settings icon (⚙️) in the top right
2. Choose your preferred download directory
3. Settings are saved automatically

### Switching Services

- Click the "Change Service" button in the navigation bar to return to the service selector
- No need to restart the app!

## 🛠️ Tech Stack

- **Electron** - Desktop application framework
- **Sharp** - High-performance image processing for PNG conversion
- **@cliqz/adblocker-electron** - Ad blocking functionality
- **darkreader** - Automatic dark mode for all websites
- **electron-store** - Settings persistence

## 📁 Project Structure

```
snapseek/
├── main.js           # Main Electron process
├── preload.js        # Preload script for IPC bridge
├── inject.js         # Content script for image overlays
├── index.html        # Service selector UI
├── settings.html     # Settings page
├── styles.css        # Application styling
├── renderer.js       # Renderer process logic
└── package.json      # Project configuration
```

## 🔧 Development

Run in development mode:
```bash
npm run dev
```

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## 📄 License

This project is licensed under the MIT License.

## ⚠️ Disclaimer

This application is for educational purposes. Please respect the terms of service of the websites you browse.

import { HistoryItem } from './types';

export function initHistory() {
    const historyList = document.getElementById('history-list');
    const clearBtn = document.getElementById('clear-history-btn');

    async function loadHistory() {
        if (window.snapseek) {
            const history = await window.snapseek.getDownloadHistory();
            renderHistory(history);
        }
    }

    window.addEventListener('route-history', () => {
        loadHistory();
    });

    if (clearBtn) {
        clearBtn.addEventListener('click', async () => {
            if (window.snapseek && confirm('Clear download history?')) {
                await window.snapseek.clearDownloadHistory();
                renderHistory([]);
            }
        });
    }

    window.openFile = async () => {
        // Open downloads folder as fallback since openFile wasn't in original IPC
        if (window.snapseek) {
            await window.snapseek.openDownloadsFolder();
        }
    };

    function renderHistory(items: HistoryItem[]) {
        if (!historyList) return;

        if (!items || items.length === 0) {
            historyList.innerHTML = `
                <div style="text-align: center; color: var(--text-secondary); padding: 40px;">
                    No downloads yet.
                </div>
            `;
            return;
        }

        historyList.innerHTML = items.map(item => {
            let hostname = 'Unknown';
            try {
                hostname = new URL(item.url).hostname;
            } catch (e) { }

            return `
            <div class="history-item">
                <div class="history-info">
                    <span class="history-filename" title="${item.filename}">${item.filename}</span>
                    <div class="history-meta">
                        <span>${new Date(item.timestamp).toLocaleString()}</span>
                        <span style="opacity: 0.5;">•</span>
                        <span>${hostname}</span>
                    </div>
                </div>
                <button class="open-file-btn" onclick="openFile('${item.path.replace(/\\/g, '\\\\')}')">
                    Open Folder
                </button>
            </div>
        `}).join('');
    }
}

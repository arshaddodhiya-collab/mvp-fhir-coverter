/* ===================================================================
   FHIR Converter Dashboard — Client-Side Logic
   =================================================================== */

(function () {
    'use strict';

    // ---------- API Base ----------
    const API = '/api/convert';

    // ---------- Sample Data ----------
    const SAMPLES = {
        hl7: [
            'MSH|^~\\&|HIS|HOSP|NHCX|GOV|20240101120000||ADT^A01|MSG001|P|2.5',
            'PID|1||ABHA123||Sharma^Rahul||19900415|M',
            'IN1|1|POL123||ICICI Lombard|||||||||20240101|20241231',
            'DG1|1||COVID19^COVID-19^ICD10|||A',
            'PR1|1||VENT^Mechanical Ventilation^CPT|Ventilator Support|20240105'
        ].join('\r'),

        json: JSON.stringify({
            abhaId: 'ABHA123',
            familyName: 'Sharma',
            givenName: 'Rahul',
            dateOfBirth: '19900415',
            gender: 'M',
            diagnoses: [{ code: 'COVID19', description: 'COVID-19' }]
        }, null, 2),

        csv: [
            'abhaId,familyName,givenName,dateOfBirth,gender,diagCode,diagDesc',
            'ABHA123,Sharma,Rahul,19900415,M,COVID19,COVID-19'
        ].join('\n')
    };

    const PLACEHOLDERS = {
        hl7: 'Paste your HL7 pipe-delimited message here…',
        json: 'Paste your JSON object here…',
        csv: 'Paste your CSV data here (first row = header)…'
    };

    // ---------- State ----------
    let currentFormat = 'hl7';
    let historyData = [];
    let dlqData = [];

    // ---------- DOM Refs ----------
    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const inputArea = $('#input-data');
    const outputPlaceholder = $('#output-placeholder');
    const outputJson = $('#output-json');
    const outputCode = $('#output-code');
    const btnConvert = $('#btn-convert');
    const btnSample = $('#btn-sample');
    const btnClear = $('#btn-clear');
    const btnCopy = $('#btn-copy');
    const btnRefreshHistory = $('#btn-refresh-history');
    const historyTbody = $('#history-tbody');
    const historyEmpty = $('#history-empty');
    const historyTable = $('#history-table');
    const modalOverlay = $('#modal-overlay');
    const modalCode = $('#modal-code');
    const modalClose = $('#modal-close');
    
    const btnRefreshDlq = $('#btn-refresh-dlq');
    const dlqTbody = $('#dlq-tbody');
    const dlqEmpty = $('#dlq-empty');
    const dlqTable = $('#dlq-table');
    const dlqModalOverlay = $('#dlq-modal-overlay');
    const dlqModalCode = $('#dlq-modal-code');
    const dlqModalClose = $('#dlq-modal-close');

    // ---------- Initialise ----------
    function init() {
        bindNavigation();
        bindTabs();
        bindButtons();
        bindModal();
        loadHistory();
        loadDlq();
    }

    // ===================================================================
    // NAVIGATION
    // ===================================================================
    function bindNavigation() {
        $$('.header__link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const section = link.dataset.section;
                $$('.header__link').forEach(l => l.classList.remove('active'));
                link.classList.add('active');
                showSection(section);
            });
        });
    }

    function showSection(name) {
        $('#converter').style.display = name === 'converter' ? '' : 'none';
        $('#history').style.display = name === 'history' ? '' : 'none';
        $('#dlq').style.display = name === 'dlq' ? '' : 'none';
        if (name === 'history') loadHistory();
        if (name === 'dlq') loadDlq();
    }

    // ===================================================================
    // TABS
    // ===================================================================
    function bindTabs() {
        $$('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                $$('.tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                currentFormat = tab.dataset.format;
                inputArea.placeholder = PLACEHOLDERS[currentFormat];
            });
        });
    }

    // ===================================================================
    // BUTTONS
    // ===================================================================
    function bindButtons() {
        btnConvert.addEventListener('click', doConvert);
        btnSample.addEventListener('click', () => {
            inputArea.value = SAMPLES[currentFormat];
            inputArea.focus();
        });
        btnClear.addEventListener('click', () => {
            inputArea.value = '';
            hideOutput();
            inputArea.focus();
        });
        btnCopy.addEventListener('click', copyOutput);
        btnRefreshHistory.addEventListener('click', loadHistory);
        btnRefreshDlq.addEventListener('click', loadDlq);
    }

    // ===================================================================
    // CONVERT
    // ===================================================================
    async function doConvert() {
        const raw = inputArea.value.trim();
        if (!raw) {
            toast('Please enter some input data.', 'error');
            return;
        }

        const endpointMap = { hl7: '/coverage', json: '/json', csv: '/csv' };
        const contentTypeMap = { hl7: 'text/plain', json: 'application/json', csv: 'text/plain' };

        const url = API + endpointMap[currentFormat];
        const contentType = contentTypeMap[currentFormat];

        // Show loading
        btnConvert.disabled = true;
        const origHTML = btnConvert.innerHTML;
        btnConvert.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Converting…';
        showLoading($('#output-wrapper'));

        try {
            const resp = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': contentType },
                body: raw
            });
            const text = await resp.text();

            if (!resp.ok) {
                throw new Error(text || `Server returned ${resp.status}`);
            }

            // Try to parse & prettify
            let parsed;
            try { parsed = JSON.parse(text); } catch (_) { parsed = null; }

            if (parsed) {
                showOutput(parsed);
                toast('Conversion successful!', 'success');
            } else {
                outputCode.textContent = text;
                outputPlaceholder.style.display = 'none';
                outputJson.style.display = '';
                btnCopy.disabled = false;
                toast('Conversion returned non-JSON response.', 'info');
            }

            // Refresh stats
            loadHistory();
            loadDlq();

        } catch (err) {
            toast(`Conversion failed: ${err.message}`, 'error');
        } finally {
            btnConvert.disabled = false;
            btnConvert.innerHTML = origHTML;
            hideLoading($('#output-wrapper'));
        }
    }

    // ===================================================================
    // OUTPUT
    // ===================================================================
    function showOutput(json) {
        outputPlaceholder.style.display = 'none';
        outputJson.style.display = '';
        outputCode.innerHTML = syntaxHighlight(JSON.stringify(json, null, 2));
        btnCopy.disabled = false;
    }

    function hideOutput() {
        outputPlaceholder.style.display = '';
        outputJson.style.display = 'none';
        outputCode.innerHTML = '';
        btnCopy.disabled = true;
    }

    function copyOutput() {
        const text = outputCode.textContent;
        if (!text) return;
        navigator.clipboard.writeText(text).then(() => {
            toast('Copied to clipboard!', 'success');
            const icon = btnCopy.querySelector('i');
            icon.className = 'fa-solid fa-check';
            setTimeout(() => { icon.className = 'fa-regular fa-copy'; }, 1500);
        });
    }

    // ===================================================================
    // SYNTAX HIGHLIGHTING
    // ===================================================================
    function syntaxHighlight(json) {
        return json.replace(
            /("(\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
            (match) => {
                let cls = 'json-num';
                if (/^"/.test(match)) {
                    cls = /:$/.test(match) ? 'json-key' : 'json-str';
                } else if (/true|false/.test(match)) {
                    cls = 'json-bool';
                } else if (/null/.test(match)) {
                    cls = 'json-null';
                }
                return `<span class="${cls}">${match}</span>`;
            }
        );
    }

    // ===================================================================
    // HISTORY
    // ===================================================================
    async function loadHistory() {
        try {
            const resp = await fetch(API + '/history');
            if (!resp.ok) throw new Error('Failed to load history');
            historyData = await resp.json();

            renderHistory();
            updateStats();
        } catch (err) {
            console.warn('History load failed:', err);
            // Non-blocking — stats will show "—"
        }
    }

    function renderHistory() {
        if (!historyData.length) {
            historyTable.style.display = 'none';
            historyEmpty.style.display = '';
            return;
        }

        historyTable.style.display = '';
        historyEmpty.style.display = 'none';

        // Sort newest first
        const sorted = [...historyData].sort((a, b) => {
            return new Date(b.createdAt) - new Date(a.createdAt);
        });

        historyTbody.innerHTML = sorted.map(r => {
            const statusClass = r.status === 'SUCCESS' ? 'success' : 'error';
            const statusIcon = r.status === 'SUCCESS' ? 'fa-circle-check' : 'fa-triangle-exclamation';
            const ts = formatTimestamp(r.createdAt);
            const errMsg = r.errorMessage
                ? `<span style="color:var(--error);font-size:.78rem;">${escapeHtml(truncate(r.errorMessage, 60))}</span>`
                : '<span style="color:var(--text-muted);">—</span>';
            const viewBtn = r.fhirJson
                ? `<button class="btn btn--secondary btn--sm view-fhir-btn" data-id="${r.id}"><i class="fa-solid fa-eye"></i> View</button>`
                : '';

            return `<tr>
                <td><strong>#${r.id}</strong></td>
                <td><span class="badge badge--${statusClass}"><i class="fa-solid ${statusIcon}"></i> ${r.status}</span></td>
                <td>${ts}</td>
                <td>${errMsg}</td>
                <td>${viewBtn}</td>
            </tr>`;
        }).join('');

        // Re-bind view buttons
        $$('.view-fhir-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.dataset.id, 10);
                const record = historyData.find(r => r.id === id);
                if (record && record.fhirJson) {
                    openModal(record.fhirJson);
                }
            });
        });
    }

    // ===================================================================
    // DLQ
    // ===================================================================
    async function loadDlq() {
        try {
            const resp = await fetch(API + '/errors');
            if (!resp.ok) throw new Error('Failed to load DLQ');
            dlqData = await resp.json();

            renderDlq();
        } catch (err) {
            console.warn('DLQ load failed:', err);
        }
    }

    function renderDlq() {
        if (!dlqData.length) {
            dlqTable.style.display = 'none';
            dlqEmpty.style.display = '';
            return;
        }

        dlqTable.style.display = '';
        dlqEmpty.style.display = 'none';

        // Sort newest first
        const sorted = [...dlqData].sort((a, b) => {
            return new Date(b.createdAt) - new Date(a.createdAt);
        });

        dlqTbody.innerHTML = sorted.map(r => {
            const ts = formatTimestamp(r.createdAt);
            const errMsg = r.errorCause || r.errorMessage
                ? `<span style="color:var(--error);font-size:.85rem;">${escapeHtml(r.errorCause || r.errorMessage)}</span>`
                : '<span style="color:var(--text-muted);">—</span>';
            const viewBtn = r.rawMessage
                ? `<button class="btn btn--secondary btn--sm view-dlq-btn" data-id="${r.id}"><i class="fa-solid fa-eye"></i> View Raw</button>`
                : '';

            return `<tr>
                <td><strong>#${r.id}</strong></td>
                <td>${errMsg}</td>
                <td>${ts}</td>
                <td>${viewBtn}</td>
            </tr>`;
        }).join('');

        // Re-bind view buttons
        $$('.view-dlq-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.dataset.id, 10);
                const record = dlqData.find(r => r.id === id);
                if (record && record.rawMessage) {
                    openDlqModal(record.rawMessage);
                }
            });
        });
    }

    // ===================================================================
    // STATS
    // ===================================================================
    function updateStats() {
        const total = historyData.length;
        const success = historyData.filter(r => r.status === 'SUCCESS').length;
        const errors = total - success;
        const rate = total > 0 ? Math.round((success / total) * 100) : 0;

        animateValue('stat-total-value', total);
        $('#stat-success-value').textContent = total > 0 ? rate + '%' : '—';
        animateValue('stat-error-value', errors);

        // Latest
        if (total > 0) {
            const sorted = [...historyData].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
            $('#stat-latest-value').textContent = formatTimestamp(sorted[0].createdAt);
        } else {
            $('#stat-latest-value').textContent = '—';
        }
    }

    function animateValue(elemId, target) {
        const el = document.getElementById(elemId);
        if (!el) return;
        const start = parseInt(el.textContent, 10) || 0;
        if (start === target) { el.textContent = target; return; }

        const duration = 400;
        const startTime = performance.now();

        function step(now) {
            const elapsed = now - startTime;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic
            el.textContent = Math.round(start + (target - start) * eased);
            if (progress < 1) requestAnimationFrame(step);
        }
        requestAnimationFrame(step);
    }

    // ===================================================================
    // MODAL
    // ===================================================================


    function openModal(jsonStr) {
        let pretty;
        try {
            pretty = JSON.stringify(JSON.parse(jsonStr), null, 2);
        } catch (_) {
            pretty = jsonStr;
        }
        modalCode.innerHTML = syntaxHighlight(pretty);
        modalOverlay.style.display = '';
        document.body.style.overflow = 'hidden';
    }

    function closeModal() {
        modalOverlay.style.display = 'none';
        document.body.style.overflow = '';
    }

    function openDlqModal(rawStr) {
        dlqModalCode.textContent = rawStr;
        dlqModalOverlay.style.display = '';
        document.body.style.overflow = 'hidden';
    }

    function closeDlqModal() {
        dlqModalOverlay.style.display = 'none';
        document.body.style.overflow = '';
    }

    function bindModal() {
        modalClose.addEventListener('click', closeModal);
        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) closeModal();
        });
        
        dlqModalClose.addEventListener('click', closeDlqModal);
        dlqModalOverlay.addEventListener('click', (e) => {
            if (e.target === dlqModalOverlay) closeDlqModal();
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                closeModal();
                closeDlqModal();
            }
        });
    }

    // ===================================================================
    // LOADING OVERLAY
    // ===================================================================
    function showLoading(container) {
        container.style.position = 'relative';
        const overlay = document.createElement('div');
        overlay.className = 'loading-overlay';
        overlay.innerHTML = '<div class="spinner"></div><span>Converting…</span>';
        container.appendChild(overlay);
    }

    function hideLoading(container) {
        const overlay = container.querySelector('.loading-overlay');
        if (overlay) overlay.remove();
    }

    // ===================================================================
    // TOAST NOTIFICATIONS
    // ===================================================================
    function toast(msg, type = 'info') {
        const container = $('#toast-container');
        const iconMap = {
            success: 'fa-circle-check',
            error: 'fa-circle-xmark',
            info: 'fa-circle-info'
        };

        const el = document.createElement('div');
        el.className = `toast toast--${type}`;
        el.innerHTML = `
            <span class="toast__icon"><i class="fa-solid ${iconMap[type] || iconMap.info}"></i></span>
            <span class="toast__msg">${escapeHtml(msg)}</span>
            <button class="toast__close"><i class="fa-solid fa-xmark"></i></button>
        `;

        container.appendChild(el);

        // Auto-remove after 4s
        const timer = setTimeout(() => removeToast(el), 4000);

        el.querySelector('.toast__close').addEventListener('click', () => {
            clearTimeout(timer);
            removeToast(el);
        });
    }

    function removeToast(el) {
        el.style.animation = 'slideOutRight .3s ease forwards';
        el.addEventListener('animationend', () => el.remove());
    }

    // ===================================================================
    // UTILITIES
    // ===================================================================
    function formatTimestamp(ts) {
        if (!ts) return '—';
        try {
            const d = new Date(ts);
            if (isNaN(d.getTime())) return ts;
            return d.toLocaleString('en-IN', {
                day: '2-digit', month: 'short', year: 'numeric',
                hour: '2-digit', minute: '2-digit', hour12: true
            });
        } catch (_) { return ts; }
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    function truncate(str, max) {
        return str.length > max ? str.slice(0, max) + '…' : str;
    }

    // ---------- Boot ----------
    document.addEventListener('DOMContentLoaded', init);

})();

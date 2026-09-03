import styles from './styles.css';

export function injectStyles() {
    if (!document.getElementById('payment-gateway-styles')) {
        const styleEl = document.createElement('style');
        styleEl.id = 'payment-gateway-styles';
        styleEl.textContent = styles.toString();
        document.head.appendChild(styleEl);
    }
}

export function createModalElement(iframeUrl, onCloseClick) {
    injectStyles();

    const modal = document.createElement('div');
    modal.id = 'payment-gateway-modal';
    modal.setAttribute('data-testid', 'payment-modal');

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';

    const content = document.createElement('div');
    content.className = 'modal-content';

    const iframe = document.createElement('iframe');
    iframe.setAttribute('data-testid', 'payment-iframe');
    iframe.src = iframeUrl;
    iframe.allow = 'payment';

    const closeBtn = document.createElement('button');
    closeBtn.setAttribute('data-testid', 'close-modal-button');
    closeBtn.className = 'close-button';
    closeBtn.innerHTML = '&times;';
    closeBtn.setAttribute('aria-label', 'Close Payment Modal');
    closeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (typeof onCloseClick === 'function') {
            onCloseClick();
        }
    });

    content.appendChild(closeBtn);
    content.appendChild(iframe);
    overlay.appendChild(content);
    modal.appendChild(overlay);

    return modal;
}

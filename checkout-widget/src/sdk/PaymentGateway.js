import { createModalElement } from './modal.js';

class PaymentGateway {
    /**
     * @param {Object} options
     * @param {string} options.key - Merchant API Key (e.g. key_test_abc123)
     * @param {string} options.orderId - Order ID (e.g. order_xxx)
     * @param {Function} [options.onSuccess] - Callback when payment succeeds
     * @param {Function} [options.onFailure] - Callback when payment fails
     * @param {Function} [options.onClose] - Callback when modal is closed
     */
    constructor(options) {
        if (!options || typeof options !== 'object') {
            throw new Error('PaymentGateway requires an options object');
        }

        if (!options.key) {
            throw new Error('PaymentGateway: "key" is required in options');
        }

        if (!options.orderId) {
            throw new Error('PaymentGateway: "orderId" is required in options');
        }

        this.key = options.key;
        this.orderId = options.orderId;
        this.onSuccess = typeof options.onSuccess === 'function' ? options.onSuccess : () => {};
        this.onFailure = typeof options.onFailure === 'function' ? options.onFailure : () => {};
        this.onClose = typeof options.onClose === 'function' ? options.onClose : () => {};

        this.modalElement = null;
        this.messageListener = null;
        this.isOpen = false;
        this.baseUrl = options.baseUrl || 'http://localhost:3001';
    }

    /**
     * Opens the payment modal and loads the embedded checkout iframe.
     */
    open() {
        if (this.isOpen && this.modalElement) {
            return;
        }

        const iframeUrl = `${this.baseUrl}/checkout?order_id=${encodeURIComponent(this.orderId)}&key=${encodeURIComponent(this.key)}&embedded=true`;

        this.modalElement = createModalElement(iframeUrl, () => {
            this.close();
        });

        // Set up cross-origin postMessage listener
        this.messageListener = (event) => {
            if (!event.data || typeof event.data !== 'object') {
                return;
            }

            const { type, data } = event.data;

            if (type === 'payment_success') {
                try {
                    this.onSuccess(data);
                } catch (err) {
                    console.error('Error in onSuccess callback:', err);
                }
                this.close();
            } else if (type === 'payment_failed') {
                try {
                    this.onFailure(data);
                } catch (err) {
                    console.error('Error in onFailure callback:', err);
                }
            } else if (type === 'close_modal') {
                this.close();
            }
        };

        window.addEventListener('message', this.messageListener);
        document.body.appendChild(this.modalElement);
        this.isOpen = true;
    }

    /**
     * Programmatically closes the payment modal and triggers the onClose callback.
     */
    close() {
        if (!this.isOpen && !this.modalElement) {
            return;
        }

        if (this.messageListener) {
            window.removeEventListener('message', this.messageListener);
            this.messageListener = null;
        }

        if (this.modalElement && this.modalElement.parentNode) {
            this.modalElement.parentNode.removeChild(this.modalElement);
        }

        this.modalElement = null;
        this.isOpen = false;

        // Trigger the onClose callback
        try {
            if (typeof this.onClose === 'function') {
                this.onClose();
            }
        } catch (err) {
            console.error('Error in onClose callback:', err);
        }
    }
}

// Expose globally for script tag integration
if (typeof window !== 'undefined') {
    window.PaymentGateway = PaymentGateway;
}

export default PaymentGateway;
export { PaymentGateway };

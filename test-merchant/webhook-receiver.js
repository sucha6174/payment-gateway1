const express = require('express');
const crypto = require('crypto');

const app = express();
app.use(express.json());

const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET || 'whsec_test_abc123';
const PORT = process.env.PORT || 4000;

app.post('/webhook', (req, res) => {
    const signature = req.headers['x-webhook-signature'];
    const payload = JSON.stringify(req.body);

    const expectedSignature = crypto
        .createHmac('sha256', WEBHOOK_SECRET)
        .update(payload)
        .digest('hex');

    if (!signature || signature.toLowerCase() !== expectedSignature.toLowerCase()) {
        console.log('❌ Invalid signature');
        console.log('Received:', signature);
        console.log('Expected:', expectedSignature);
        return res.status(401).send('Invalid signature');
    }

    console.log('✅ Webhook verified:', req.body.event);
    if (req.body.data && req.body.data.payment) {
        console.log('Payment ID:', req.body.data.payment.id);
    } else if (req.body.data && req.body.data.refund) {
        console.log('Refund ID:', req.body.data.refund.id);
    }

    res.status(200).json({ status: 'received', event: req.body.event });
});

app.listen(PORT, () => {
    console.log(`Test merchant webhook running on port ${PORT}`);
});

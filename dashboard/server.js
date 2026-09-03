const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.static(path.join(__dirname, 'public')));

// Secure session endpoint supplying merchant test credentials from environment
app.get('/api/session', (req, res) => {
    res.json({
        apiKey: process.env.MERCHANT_API_KEY || 'key_test_abc123',
        apiSecret: process.env.MERCHANT_API_SECRET || 'secret_test_xyz789'
    });
});

// Webhook dashboard routes
app.get(['/webhooks', '/dashboard/webhooks'], (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'webhooks.html'));
});

// API Documentation routes
app.get(['/docs', '/dashboard/docs'], (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'docs.html'));
});

// Root route
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
    console.log(`Merchant Dashboard service running on http://localhost:${PORT}`);
});

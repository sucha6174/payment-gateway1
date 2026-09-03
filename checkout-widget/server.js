const express = require('express');
const path = require('path');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());

// Serve static bundled checkout.js from dist
app.use('/checkout.js', (req, res) => {
    res.sendFile(path.join(__dirname, 'dist', 'checkout.js'));
});

// Serve dist directory
app.use('/dist', express.static(path.join(__dirname, 'dist')));

// Serve checkout iframe page
app.get('/checkout', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'checkout.html'));
});

// Serve root demo page
app.use(express.static(path.join(__dirname, 'public')));

app.listen(PORT, () => {
    console.log(`Checkout and SDK service running on http://localhost:${PORT}`);
});

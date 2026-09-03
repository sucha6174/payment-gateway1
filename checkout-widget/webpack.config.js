const path = require('path');

module.exports = {
    entry: './src/sdk/PaymentGateway.js',
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'checkout.js',
        library: {
            name: 'PaymentGateway',
            type: 'umd',
            export: 'default',
        },
        globalObject: 'typeof self !== "undefined" ? self : this',
        clean: true,
    },
    module: {
        rules: [
            {
                test: /\.css$/i,
                type: 'asset/source',
            },
        ],
    },
    mode: 'production',
};
